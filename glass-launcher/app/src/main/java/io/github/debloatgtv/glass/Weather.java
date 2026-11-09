package io.github.debloatgtv.glass;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current weather line ("28° · Hujan · Terasa 33°") from Open-Meteo: free, no
 * key, no account. Uses the prayer-times location; refreshed at most every
 * 30 minutes and cached so Home never waits on the network.
 */
final class Weather {

    private static final long MAX_AGE = 30 * 60 * 1000L;
    private final SharedPreferences prefs;

    Weather(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    /** Last known line, or null. */
    String line() {
        return prefs.getString("line", null);
    }

    boolean stale() {
        return System.currentTimeMillis() - prefs.getLong("at", 0) > MAX_AGE;
    }

    /** Fetches the current weather. Background thread only. */
    void refresh(double[] loc, boolean malay) {
        if (loc == null || !stale()) return;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,apparent_temperature,weather_code&timezone=auto",
                    loc[0], loc[1]);
            JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
            long t = Math.round(cur.getDouble("temperature_2m"));
            long feels = Math.round(cur.getDouble("apparent_temperature"));
            String sky = describe(cur.getInt("weather_code"), malay);
            String line = t + "°  ·  " + sky + (feels != t ? "  ·  " + (malay ? "Terasa " : "Feels ") + feels + "°" : "");
            prefs.edit().putString("line", line).putLong("at", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
            // Offline: keep showing the last line.
        }
    }

    /** WMO weather codes, as used by Open-Meteo. */
    static String describe(int code, boolean malay) {
        if (code == 0) return malay ? "Cerah" : "Clear";
        if (code <= 2) return malay ? "Berawan" : "Partly cloudy";
        if (code == 3) return malay ? "Mendung" : "Cloudy";
        if (code == 45 || code == 48) return malay ? "Berkabus" : "Fog";
        if (code >= 51 && code <= 57) return malay ? "Gerimis" : "Drizzle";
        if (code >= 61 && code <= 67) return malay ? "Hujan" : "Rain";
        if (code >= 80 && code <= 82) return malay ? "Hujan lebat" : "Showers";
        if (code >= 95) return malay ? "Ribut petir" : "Thunderstorm";
        if (code >= 71 && code <= 86) return malay ? "Salji" : "Snow";
        return malay ? "Berawan" : "Cloudy";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "GlassLauncher/1.0 (Android TV)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
