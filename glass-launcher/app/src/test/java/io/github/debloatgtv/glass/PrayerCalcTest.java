package io.github.debloatgtv.glass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Keeps the offline calculation honest against official and astronomical references. */
public class PrayerCalcTest {

    /** Official JAKIM e-Solat times, zone WLY01 (Kuala Lumpur), 2026. */
    private static final String[][] JAKIM_WLY01 = {
            {"2026-01-01", "06:06", "07:18", "13:19", "16:42", "19:17", "20:31"},
            {"2026-02-15", "06:19", "07:26", "13:30", "16:49", "19:29", "20:40"},
            {"2026-04-01", "06:05", "07:12", "13:20", "16:22", "19:23", "20:33"},
            {"2026-05-16", "05:50", "07:01", "13:12", "16:35", "19:20", "20:33"},
            {"2026-06-30", "05:54", "07:07", "13:20", "16:45", "19:29", "20:44"},
            {"2026-08-14", "06:01", "07:10", "13:21", "16:38", "19:27", "20:38"},
            {"2026-09-28", "05:54", "07:00", "13:07", "16:15", "19:09", "20:17"},
            {"2026-11-08", "05:48", "06:56", "13:00", "16:21", "18:58", "20:10"},
            {"2026-12-07", "05:54", "07:05", "13:07", "16:30", "19:05", "20:19"},
            {"2026-12-31", "06:06", "07:17", "13:19", "16:41", "19:16", "20:31"},
    };

    private static int hm(String s) {
        return Integer.parseInt(s.substring(0, 2)) * 60 + Integer.parseInt(s.substring(3, 5));
    }

    @Test
    public void jakimFallbackWithinOneMinuteOfOfficial() {
        PrayerCalc.Method m = PrayerCalc.method("jakim");
        for (String[] row : JAKIM_WLY01) {
            String[] d = row[0].split("-");
            int[] t = PrayerCalc.compute(Integer.parseInt(d[0]), Integer.parseInt(d[1]), Integer.parseInt(d[2]),
                    3.1390, 101.6869, 8, m, false, null);
            for (int i = 0; i < 6; i++) {
                int diff = Math.abs(t[i] - hm(row[i + 1]));
                assertTrue(row[0] + " prayer " + i + " off by " + diff + " min", diff <= 1);
            }
        }
    }

    /** London, 8 Nov 2026 (GMT): sunrise/noon/sunset per NOAA are 07:06, 11:44, 16:20. */
    @Test
    public void londonMatchesNoaaSun() {
        int[] t = PrayerCalc.compute(2026, 11, 8, 51.5074, -0.1278, 0, PrayerCalc.method("mwl"), false, null);
        assertEquals(7 * 60 + 6, t[PrayerCalc.SUNRISE], 1);
        assertEquals(11 * 60 + 45, t[PrayerCalc.DHUHR], 1);
        assertEquals(16 * 60 + 21, t[PrayerCalc.MAGHRIB], 1);
    }

    /** Summer at high latitude: 18° twilight never ends, the high-latitude rule must still give times. */
    @Test
    public void highLatitudeSummerStillHasFajrAndIsha() {
        int[] t = PrayerCalc.compute(2026, 6, 21, 59.3293, 18.0686, 2, PrayerCalc.method("mwl"), false, null);
        assertTrue(t[PrayerCalc.FAJR] > 0 && t[PrayerCalc.FAJR] < t[PrayerCalc.SUNRISE]);
        assertTrue(t[PrayerCalc.ISHA] > t[PrayerCalc.MAGHRIB]);
    }

    @Test
    public void methodFromTimeZone() {
        assertEquals("jakim", PrayerCalc.methodForTimeZone("Asia/Kuala_Lumpur"));
        assertEquals("muis", PrayerCalc.methodForTimeZone("Asia/Singapore"));
        assertEquals("kemenag", PrayerCalc.methodForTimeZone("Asia/Jakarta"));
        assertEquals("isna", PrayerCalc.methodForTimeZone("America/Chicago"));
        assertEquals("mwl", PrayerCalc.methodForTimeZone("Europe/London"));
    }
}
