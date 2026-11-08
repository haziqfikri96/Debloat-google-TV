package io.github.debloatgtv.glass;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeKeeperTest {
    @Test
    public void reactsOnlyToTheStockHomeScreen() {
        // Google TV home (seen on a Xiaomi TV): take over.
        assertTrue(HomeKeeperService.isStockHome("com.google.android.apps.tv.launcherx",
                "com.google.android.apps.tv.launcherx.home.HomeActivity"));
        assertTrue(HomeKeeperService.isStockHome("com.google.android.tvlauncher",
                "com.google.android.tvlauncher.MainActivity"));
        // Its side panels / dashboard and other apps: leave alone.
        assertFalse(HomeKeeperService.isStockHome("com.google.android.apps.tv.launcherx",
                "com.google.android.apps.tv.launcherx.dashboard.DashboardActivity"));
        assertFalse(HomeKeeperService.isStockHome("com.google.android.apps.tv.dreamx",
                "com.google.android.libraries.tv.smarthome.core.SmartHomeMainActivity"));
        assertFalse(HomeKeeperService.isStockHome("com.netflix.ninja", "com.netflix.ninja.MainActivity"));
        assertFalse(HomeKeeperService.isStockHome(null, null));
    }
}
