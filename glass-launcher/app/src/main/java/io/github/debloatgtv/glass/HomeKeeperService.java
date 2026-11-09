package io.github.debloatgtv.glass;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens Glass Launcher whenever the stock Google TV / Android TV home screen
 * comes to the front (HOME button, wake from sleep, after exiting an app).
 *
 * Google TV ignores the "default home app" setting on many TVs, so this is the
 * same approach Projectivy uses: watch for the stock home window and jump in
 * front of it. The stock launcher stays enabled, which keeps the Google Home
 * panel and long-press HOME working. Only window-change events from the stock
 * launcher packages are delivered (see res/xml/home_keeper.xml); no screen
 * content is read.
 */
public class HomeKeeperService extends AccessibilityService {

    private long lastLaunch;
    private static HomeKeeperService running;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
    }

    /** Opens the TV's power menu. False when this service is not turned on. */
    static boolean showPowerMenu() {
        HomeKeeperService s = running;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = e.getClassName();
        if (!isStockHome(e.getPackageName(), cls)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < 700) return; // one launch per HOME press
        lastLaunch = now;
        Intent i = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(i);
        } catch (RuntimeException ignored) {
            // Launcher being updated or disabled: nothing to do.
        }
    }

    /** The stock launcher's main home screen, not its side panels or dialogs. */
    static boolean isStockHome(CharSequence pkg, CharSequence cls) {
        if (pkg == null || cls == null) return false;
        String p = pkg.toString(), c = cls.toString();
        boolean stock = p.equals("com.google.android.apps.tv.launcherx")
                || p.equals("com.google.android.tvlauncher")
                || p.equals("com.google.android.leanbacklauncher");
        if (!stock) return false;
        return c.endsWith("HomeActivity") || c.endsWith(".MainActivity") || c.endsWith("LauncherActivity");
    }

    @Override
    public void onInterrupt() {}
}
