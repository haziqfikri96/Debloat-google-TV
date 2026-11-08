package io.github.debloatgtv.glass;

/**
 * Offline prayer-time calculation (the well-known PrayTimes.org astronomical
 * algorithm). Pure Java with no Android dependencies, so it can be unit-tested
 * on any JVM.
 *
 * Times are returned as minutes after local midnight, already including each
 * method's offsets and rounded to the safe side (prayers round up, sunrise
 * rounds down).
 */
final class PrayerCalc {

    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    /** A calculation method: twilight angles plus per-prayer minute offsets. */
    static final class Method {
        final String id, name;
        final double fajrAngle;
        final double ishaAngle;      // used when ishaMinutes == 0
        final double ishaMinutes;    // fixed interval after maghrib (Umm al-Qura, Gulf, Qatar)
        final double maghribAngle;   // 0 = sunset
        final double[] offsets;      // minutes added per prayer (ihtiyat)
        final boolean hanafiDefault;

        Method(String id, String name, double fajr, double isha, double ishaMin, double maghrib,
               double[] offsets, boolean hanafi) {
            this.id = id; this.name = name; fajrAngle = fajr; ishaAngle = isha; ishaMinutes = ishaMin;
            maghribAngle = maghrib; this.offsets = offsets; hanafiDefault = hanafi;
        }
    }

    private static final double[] NONE = {0, 0, 0, 0, 0, 0};

    static final Method[] METHODS = {
            // JAKIM (Malaysia): Subuh 18°, Isyak 18°. Offsets fitted against the
            // official 2026 timetable (e-solat.gov.my); used only when the official
            // zone timetable can't be downloaded.
            new Method("jakim", "JAKIM", 18, 18, 0, 0, new double[]{1.2, -0.3, 2.3, 1.1, 1.3, 1.5}, false),
            new Method("muis", "MUIS Singapore", 20, 18, 0, 0, new double[]{0, 0, 1, 0, 1, 0}, false),
            new Method("kemenag", "Kemenag Indonesia", 20, 18, 0, 0, new double[]{2, -2, 2, 2, 2, 2}, false),
            new Method("brunei", "KHEU Brunei", 20, 18, 0, 0, NONE, false),
            new Method("mwl", "Muslim World League", 18, 17, 0, 0, NONE, false),
            new Method("isna", "ISNA (North America)", 15, 15, 0, 0, NONE, false),
            new Method("egypt", "Egyptian Authority", 19.5, 17.5, 0, 0, NONE, false),
            new Method("makkah", "Umm al-Qura, Makkah", 18.5, 0, 90, 0, NONE, false),
            new Method("karachi", "Univ. of Islamic Sciences, Karachi", 18, 18, 0, 0, NONE, true),
            new Method("tehran", "Inst. of Geophysics, Tehran", 17.7, 14, 0, 4.5, NONE, false),
            new Method("gulf", "Gulf Region", 19.5, 0, 90, 0, NONE, false),
            new Method("kuwait", "Kuwait", 18, 17.5, 0, 0, NONE, false),
            new Method("qatar", "Qatar", 18, 0, 90, 0, NONE, false),
            new Method("dubai", "Dubai", 18.2, 18.2, 0, 0, new double[]{0, -3, 3, 3, 3, 0}, false),
            new Method("turkey", "Diyanet Turkey", 18, 17, 0, 0, new double[]{0, -7, 5, 4, 7, 0}, false),
            new Method("france", "UOIF France", 12, 12, 0, 0, NONE, false),
            new Method("russia", "Spiritual Admin. of Muslims of Russia", 16, 15, 0, 0, NONE, false),
    };

    static Method method(String id) {
        for (Method m : METHODS) if (m.id.equals(id)) return m;
        return null;
    }

    /** Picks the local authority's method from an IANA time zone ID. */
    static String methodForTimeZone(String tz) {
        if (tz == null) return "mwl";
        switch (tz) {
            case "Asia/Kuala_Lumpur": case "Asia/Kuching": return "jakim";
            case "Asia/Singapore": return "muis";
            case "Asia/Jakarta": case "Asia/Pontianak": case "Asia/Makassar": case "Asia/Jayapura": return "kemenag";
            case "Asia/Brunei": return "brunei";
            case "Asia/Riyadh": case "Asia/Aden": return "makkah";
            case "Asia/Dubai": return "dubai";
            case "Asia/Muscat": case "Asia/Bahrain": return "gulf";
            case "Asia/Kuwait": return "kuwait";
            case "Asia/Qatar": return "qatar";
            case "Asia/Karachi": case "Asia/Kolkata": case "Asia/Calcutta": case "Asia/Dhaka": case "Asia/Kabul": return "karachi";
            case "Asia/Tehran": return "tehran";
            case "Europe/Istanbul": case "Asia/Istanbul": return "turkey";
            case "Europe/Paris": return "france";
            case "Europe/Moscow": case "Europe/Kazan": case "Europe/Volgograd": return "russia";
            case "Africa/Cairo": case "Africa/Khartoum": case "Asia/Damascus": case "Asia/Beirut":
            case "Asia/Amman": case "Asia/Baghdad": case "Africa/Tripoli": case "Africa/Algiers":
            case "Africa/Casablanca": case "Africa/Tunis": return "egypt";
            default:
                if (tz.startsWith("America/") || tz.startsWith("Canada/") || tz.startsWith("US/")) return "isna";
                return "mwl";
        }
    }

    private PrayerCalc() {}

    /**
     * @param tzHours UTC offset of the location on that date (including DST)
     * @param hanafi  Hanafi Asr (shadow factor 2) instead of the standard factor 1
     * @param extra   user adjustments in minutes per prayer (may be null)
     * @return minutes after local midnight for FAJR..ISHA
     */
    static int[] compute(int year, int month, int day, double lat, double lng, double tzHours,
                         Method m, boolean hanafi, double[] extra) {
        double jd = julian(year, month, day) - lng / (15 * 24.0);

        double[] t = {5, 6, 12, 13, 18, 18}; // initial guesses in hours
        double[] p = new double[6];
        for (int i = 0; i < 6; i++) p[i] = t[i] / 24;
        double riseAngle = 0.833;
        t[FAJR] = sunAngleTime(jd, m.fajrAngle, p[FAJR], lat, true);
        t[SUNRISE] = sunAngleTime(jd, riseAngle, p[SUNRISE], lat, true);
        t[DHUHR] = midDay(jd, p[DHUHR]);
        t[ASR] = asrTime(jd, hanafi ? 2 : 1, p[ASR], lat);
        double sunset = sunAngleTime(jd, riseAngle, p[MAGHRIB], lat, false);
        t[MAGHRIB] = m.maghribAngle > 0 ? sunAngleTime(jd, m.maghribAngle, p[MAGHRIB], lat, false) : sunset;
        t[ISHA] = m.ishaMinutes > 0 ? sunset + m.ishaMinutes / 60 : sunAngleTime(jd, m.ishaAngle, p[ISHA], lat, false);

        // Convert solar to local time.
        for (int i = 0; i < 6; i++) t[i] += tzHours - lng / 15;

        adjustHighLatitudes(t, sunset + tzHours - lng / 15, m);

        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            double min = t[i] * 60 + m.offsets[i] + (extra == null ? 0 : extra[i]);
            out[i] = (int) (i == SUNRISE ? Math.floor(min) : Math.ceil(min - 1e-9));
        }
        return out;
    }

    /** "Angle-based" rule: limits Fajr/Isha where twilight never fully ends (high latitudes). */
    private static void adjustHighLatitudes(double[] t, double sunset, Method m) {
        double night = diff(sunset, t[SUNRISE]);
        double fajrMax = m.fajrAngle / 60 * night;
        if (Double.isNaN(t[FAJR]) || diff(t[FAJR], t[SUNRISE]) > fajrMax) t[FAJR] = t[SUNRISE] - fajrMax;
        if (m.ishaMinutes == 0) {
            double ishaMax = m.ishaAngle / 60 * night;
            if (Double.isNaN(t[ISHA]) || diff(sunset, t[ISHA]) > ishaMax) t[ISHA] = sunset + ishaMax;
        }
        if (m.maghribAngle > 0) {
            double magMax = m.maghribAngle / 60 * night;
            if (Double.isNaN(t[MAGHRIB]) || diff(sunset, t[MAGHRIB]) > magMax) t[MAGHRIB] = sunset + magMax;
        }
    }

    private static double diff(double a, double b) { return fix(b - a, 24); }

    // ------------------------------------------------------------ astronomy --

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** @return {declination, equation of time} */
    private static double[] sun(double jd) {
        double d = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * d, 360);
        double q = fix(280.459 + 0.98564736 * d, 360);
        double l = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = atan2(cos(e) * sin(l), cos(l)) / 15;
        double eqt = q / 15 - fix(ra, 24);
        double decl = asin(sin(e) * sin(l));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        return fix(12 - sun(jd + t)[1], 24);
    }

    private static double sunAngleTime(double jd, double angle, double t, double lat, boolean ccw) {
        double decl = sun(jd + t)[0];
        double noon = midDay(jd, t);
        double x = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat));
        if (x < -1 || x > 1) return Double.NaN; // sun never reaches this angle today
        double tt = acos(x) / 15;
        return noon + (ccw ? -tt : tt);
    }

    private static double asrTime(double jd, double factor, double t, double lat) {
        double decl = sun(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (factor + tan(Math.abs(lat - decl)))));
        return sunAngleTime(jd, angle, t, lat, false);
    }

    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double tan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double asin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double acos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double atan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }
}
