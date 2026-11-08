package io.github.debloatgtv.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Prayer times for the TV's location.
 *
 * - Malaysia: the official JAKIM e-Solat timetable for the user's zone
 *   (zone found from coordinates), downloaded once a year and cached, so it
 *   matches the mosque/JAKIM times to the minute. Falls back to calculation
 *   when offline.
 * - Elsewhere: offline astronomical calculation with the local authority's
 *   method, picked from the TV's time zone.
 *
 * Location comes from the phone (`gtv.sh prayer`), or the TV's own last known
 * location if it has one.
 */
final class Prayer {

    static final String ACTION_CONFIG = "io.github.debloatgtv.glass.CONFIG";

    static final class Day {
        int[] minutes;       // FAJR..ISHA, minutes after midnight
        String hijri;        // e.g. "28 Jamadilawal 1448", or null
        String source;       // shown under the card so users know where times come from
        boolean malay;       // Malay prayer names
    }

    private static final String[] HIJRI_MS = {"Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal",
            "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah"};
    private static final String[] HIJRI_EN = {"Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal",
            "Dhu al-Qi'dah", "Dhu al-Hijjah"};
    private static final String[] MONTHS_MS = {"Jan", "Feb", "Mac", "Apr", "Mei", "Jun", "Jul", "Ogo", "Sep",
            "Okt", "Nov", "Dis"};

    private final Context ctx;
    private final SharedPreferences prefs;
    private JSONArray official;     // JAKIM year (cached)
    private String officialKey = "";

    Prayer(Context c) {
        ctx = c.getApplicationContext();
        prefs = ctx.getSharedPreferences("prayer", Context.MODE_PRIVATE);
    }

    // --------------------------------------------------------- configuration --

    /** Settings sent by `gtv.sh prayer` as intent extras. */
    boolean configure(Bundle b) {
        if (b == null) return false;
        SharedPreferences.Editor e = prefs.edit();
        boolean changed = false;
        if (b.containsKey("lat") && b.containsKey("lng")) {
            e.putFloat("lat", b.getFloat("lat")).putFloat("lng", b.getFloat("lng"));
            e.remove("zone").remove("zoneFor"); // new place: look the zone up again
            changed = true;
        }
        for (String k : new String[]{"city", "method", "asr", "zone", "lang", "adjust"}) {
            if (b.containsKey(k)) {
                String v = b.getString(k);
                if (v == null || v.isEmpty() || v.equals("auto")) e.remove(k); else e.putString(k, v);
                if (k.equals("zone")) e.putString("zoneFor", "manual");
                changed = true;
            }
        }
        e.apply();
        if (changed) { official = null; officialKey = ""; }
        return changed;
    }

    boolean hasLocation() {
        return prefs.contains("lat") || lastKnownLocation() != null;
    }

    private double[] location() {
        if (prefs.contains("lat")) return new double[]{prefs.getFloat("lat", 0), prefs.getFloat("lng", 0)};
        Location l = lastKnownLocation();
        return l == null ? null : new double[]{l.getLatitude(), l.getLongitude()};
    }

    private Location lastKnownLocation() {
        try {
            if (Build.VERSION.SDK_INT >= 23
                    && ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    String placeLabel() {
        String city = prefs.getString("city", null);
        if (city != null) return city;
        double[] loc = location();
        return loc == null ? "" : String.format(Locale.US, "%.3f, %.3f", loc[0], loc[1]);
    }

    private String methodId() {
        String m = prefs.getString("method", null);
        if (m != null && PrayerCalc.method(m) != null) return m;
        if (zone() != null) return "jakim";
        return PrayerCalc.methodForTimeZone(TimeZone.getDefault().getID());
    }

    private String zone() {
        return prefs.getString("zone", null);
    }

    private boolean malay() {
        String lang = prefs.getString("lang", null);
        if (lang != null) return lang.equals("ms") || lang.equals("id");
        String sys = Locale.getDefault().getLanguage();
        return sys.equals("ms") || sys.equals("in") || sys.equals("id") || zone() != null;
    }

    // ------------------------------------------------------------- the times --

    /** Today's times. Never blocks on the network (call refresh() for that). */
    Day day(Calendar now) {
        double[] loc = location();
        if (loc == null) return null;
        Day d = new Day();
        d.malay = malay();

        String zone = zone();
        if (zone != null) {
            JSONObject row = officialRow(zone, now);
            if (row != null) {
                try {
                    d.minutes = new int[]{
                            hm(row.getString("fajr")), hm(row.getString("syuruk")), hm(row.getString("dhuhr")),
                            hm(row.getString("asr")), hm(row.getString("maghrib")), hm(row.getString("isha"))};
                    d.hijri = hijri(row.optString("hijri", null), true);
                    d.source = "JAKIM " + zone + " (official)";
                    applyUserAdjust(d.minutes);
                    return d;
                } catch (Exception ignored) {
                    // fall through to calculation
                }
            }
        }

        PrayerCalc.Method m = PrayerCalc.method(methodId());
        String asr = prefs.getString("asr", null);
        boolean hanafi = asr != null ? asr.equals("hanafi") : m.hanafiDefault;
        TimeZone tz = TimeZone.getDefault();
        Calendar noon = (Calendar) now.clone();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        double tzHours = tz.getOffset(noon.getTimeInMillis()) / 3600000.0;
        d.minutes = PrayerCalc.compute(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH), loc[0], loc[1], tzHours, m, hanafi, null);
        applyUserAdjust(d.minutes);
        d.hijri = icuHijri(now, d.malay);
        d.source = m.name + (hanafi ? " · Hanafi" : "") + (zone != null ? " (offline estimate)" : "");
        return d;
    }

    /** Optional "fajr=+2,isha=-1" style tweaks from `gtv.sh prayer --adjust`. */
    private void applyUserAdjust(int[] t) {
        String a = prefs.getString("adjust", null);
        if (a == null) return;
        String[] names = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};
        for (String part : a.split(",")) {
            String[] kv = part.trim().split("=");
            if (kv.length != 2) continue;
            for (int i = 0; i < 6; i++) {
                if (names[i].equalsIgnoreCase(kv[0].trim())) {
                    try { t[i] += Integer.parseInt(kv[1].trim().replace("+", "")); } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    // --------------------------------------------------------- network work --

    /** Looks up the JAKIM zone and downloads the year's timetable. Background thread only. */
    void refresh() {
        double[] loc = location();
        if (loc == null) return;
        // Zone lookup only for coordinates in/near Malaysia.
        String key = String.format(Locale.US, "%.3f,%.3f", loc[0], loc[1]);
        boolean inMalaysia = loc[0] > 0.7 && loc[0] < 7.6 && loc[1] > 99.5 && loc[1] < 119.5;
        if (inMalaysia && prefs.getString("method", null) == null
                && !"manual".equals(prefs.getString("zoneFor", "")) && !key.equals(prefs.getString("zoneFor", ""))) {
            try {
                String body = get(String.format(Locale.US, "https://api.waktusolat.app/zones/%.5f/%.5f", loc[0], loc[1]));
                JSONObject o = new JSONObject(body);
                String z = o.optString("zone", "");
                if (z.matches("[A-Z]{3}\\d{2}")) {
                    prefs.edit().putString("zone", z).putString("zoneFor", key).apply();
                }
            } catch (Exception ignored) {
                // Outside Malaysia or offline: calculation is used.
            }
        }
        String zone = zone();
        if (zone == null) return;
        int year = Calendar.getInstance().get(Calendar.YEAR);
        File f = cacheFile(zone, year);
        if (f.length() > 1000) return;
        try {
            String body = get("https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone=" + zone);
            JSONArray rows = new JSONObject(body).getJSONArray("prayerTime");
            if (rows.length() > 300) {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(rows.toString().getBytes("UTF-8"));
                }
                official = null;
                officialKey = "";
            }
        } catch (Exception ignored) {
            // offline: keep using the calculation until the next refresh
        }
    }

    private File cacheFile(String zone, int year) {
        return new File(ctx.getFilesDir(), "jakim-" + zone + "-" + year + ".json");
    }

    private JSONObject officialRow(String zone, Calendar now) {
        int year = now.get(Calendar.YEAR);
        String key = zone + year;
        if (!key.equals(officialKey)) {
            official = null;
            officialKey = key;
            File f = cacheFile(zone, year);
            if (f.length() > 1000) {
                try (InputStream in = new FileInputStream(f)) {
                    official = new JSONArray(readAll(in));
                } catch (Exception ignored) {}
            }
        }
        if (official == null) return null;
        String date = String.format(Locale.US, "%02d-%s-%d", now.get(Calendar.DAY_OF_MONTH),
                MONTHS_MS[now.get(Calendar.MONTH)], year);
        String dateEn = String.format(Locale.US, "%02d-%s-%d", now.get(Calendar.DAY_OF_MONTH),
                new java.text.DateFormatSymbols(Locale.US).getShortMonths()[now.get(Calendar.MONTH)], year);
        for (int i = 0; i < official.length(); i++) {
            JSONObject r = official.optJSONObject(i);
            if (r == null) continue;
            String d = r.optString("date");
            // JAKIM writes "Ogos" for August; compare on the 3-letter prefix.
            if (d.length() >= 6) {
                String norm = d.substring(0, 6) + d.substring(d.lastIndexOf('-'));
                String want = date.substring(0, 6) + date.substring(date.lastIndexOf('-'));
                String wantEn = dateEn.substring(0, 6) + dateEn.substring(dateEn.lastIndexOf('-'));
                if (norm.equalsIgnoreCase(want) || norm.equalsIgnoreCase(wantEn)) return r;
            }
        }
        return null;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "GlassLauncher/1.0 (Android TV)");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            return readAll(in);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    // ---------------------------------------------------------------- dates --

    private static int hm(String s) {
        String[] p = s.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** "1448-05-28" -> "28 Jamadilawal 1448". */
    private static String hijri(String iso, boolean malay) {
        if (iso == null || iso.length() < 10) return null;
        try {
            int y = Integer.parseInt(iso.substring(0, 4));
            int m = Integer.parseInt(iso.substring(5, 7));
            int d = Integer.parseInt(iso.substring(8, 10));
            return d + " " + (malay ? HIJRI_MS : HIJRI_EN)[m - 1] + " " + y;
        } catch (Exception e) {
            return null;
        }
    }

    /** Umm al-Qura Hijri date from ICU (Android 7+). */
    private static String icuHijri(Calendar now, boolean malay) {
        if (Build.VERSION.SDK_INT < 24) return null;
        try {
            android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
            ic.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            ic.setTimeInMillis(now.getTimeInMillis());
            return ic.get(android.icu.util.Calendar.DAY_OF_MONTH) + " "
                    + (malay ? HIJRI_MS : HIJRI_EN)[ic.get(android.icu.util.Calendar.MONTH)] + " "
                    + ic.get(android.icu.util.Calendar.YEAR);
        } catch (Throwable t) {
            return null;
        }
    }
}
