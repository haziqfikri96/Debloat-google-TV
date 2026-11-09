package io.github.debloatgtv.glass;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glass Launcher: a minimal, fast Android TV home screen with a frosted-glass
 * look, prayer times and a Google Home shortcut.
 */
public class HomeActivity extends Activity {

    /** Apps per dock row. */
    private static final int COLUMNS = 7;
    private Weather weather;
    private TextView weatherLine;

    /**
     * Popular apps shown as icons even when not installed. Nothing is installed
     * until the user selects one, which opens its Play Store page.
     * {package, label, brand colour}
     */
    private static final String[][] SUGGESTED = {
            {"com.google.android.youtube.tv", "YouTube", "#FF0000"},
            {"com.netflix.ninja", "Netflix", "#E50914"},
            {"com.amazon.amazonvideo.livingroom", "Prime Video", "#00A8E1"},
            {"com.disney.disneyplus", "Disney+", "#113CCF"},
            {"com.spotify.tv.android", "Spotify", "#1DB954"},
            {"com.google.android.youtube.tvmusic", "YouTube Music", "#FF0033"},
            {"com.plexapp.android", "Plex", "#E5A00D"},
            {"org.videolan.vlc", "VLC", "#FF8800"},
    };

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<App> apps = new ArrayList<>();

    private SharedPreferences prefs;
    private Prayer prayer;
    private FrameLayout root;
    private TextView time, date, hijri, styleLabel;
    private GridView grid;
    private Adapter adapter;
    private GlassFrame selectedTile;

    // glass pop-up menu (drawn inside this window so the glass lines up)
    private FrameLayout menuLayer;
    private ViewGroup content;      // everything behind a menu
    private boolean startedOnce;
    private View focusBeforeMenu;
    private GlassFrame inputsPill;

    // "Move" mode: the selected app follows the D-pad until OK / Back
    private boolean moving;
    private ObjectAnimator wiggle;

    // prayer card
    private TextView nextLabel, nextTime, nextIn, prayerSource;
    private LinearLayout prayerBody;
    private TextView prayerHint;
    private final GlassFrame[] chips = new GlassFrame[6];
    private final TextView[] chipName = new TextView[6], chipTime = new TextView[6];
    private Prayer.Day today, tomorrow;
    private int todayKey = -1;

    // ------------------------------------------------------------ lifecycle --

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("glass", MODE_PRIVATE);
        prayer = new Prayer(this);
        weather = new Weather(this);
        handleConfig(getIntent());
        buildUi();
        loadTheme(pickTheme());
        loadApps();
        // Listen for app installs/removals for the whole lifetime, not just while visible:
        // apps removed over adb or from Settings must disappear from Home.
        IntentFilter pkg = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        pkg.addDataScheme("package");
        register(packageReceiver, pkg);
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateClock();
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(clockReceiver, time);
        // Catch anything that changed while another app was on screen.
        if (startedOnce) loadApps();
        startedOnce = true;
        // A new wallpaper may have been pushed while we were in the background.
        int t = pickTheme();
        if (t != Glass.theme) loadTheme(t);
        refreshPrayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(clockReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packageReceiver); } catch (IllegalArgumentException ignored) {}
        worker.shutdownNow();
    }

    /** HOME pressed while already home: jump back to the first app. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (handleConfig(intent)) {
            refreshPrayer();
            loadApps();
            return;
        }
        if (grid != null && adapter.getCount() > 0) {
            grid.setSelection(0);
            grid.requestFocus();
        }
    }

    /** Settings pushed from the phone/PC by gtv.sh (`am start -a ...CONFIG --e...`). */
    private boolean handleConfig(Intent i) {
        if (i == null || !Prayer.ACTION_CONFIG.equals(i.getAction())) return false;
        Bundle b = i.getExtras();
        if (b == null) return true;
        prayer.configure(b);
        SharedPreferences.Editor e = prefs.edit();
        if (b.containsKey("homePanel")) {
            String c = b.getString("homePanel");
            if (c == null || c.isEmpty()) e.remove("homePanel"); else e.putString("homePanel", c);
        }
        if (b.containsKey("suggestions")) {
            String s = b.getString("suggestions", "on");
            e.putBoolean("suggestOff", s.equals("off"));
            if (s.equals("reset")) {
                for (String k : prefs.getAll().keySet()) if (k.startsWith("hide:")) e.remove(k);
                e.putBoolean("suggestOff", false);
            }
        }
        e.apply();
        return true;
    }

    @Override
    public void onBackPressed() {
        // We are the home screen: Back only closes our own menus.
        if (menuLayer != null) closeMenu();
        else if (moving) stopMoving();
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(r, f);
    }

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateClock();
            if (!Intent.ACTION_TIME_TICK.equals(i.getAction())) todayKey = -1;
            updatePrayerCard();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    // ------------------------------------------------------------------- UI --

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(float sp, int alpha, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.argb(alpha, 255, 255, 255));
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif-light", Typeface.NORMAL));
        t.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void buildUi() {
        final android.graphics.Paint shade = new android.graphics.Paint();
        root = new FrameLayout(this) {
            @Override protected void dispatchDraw(Canvas c) {
                Glass.drawBackground(c, this);
                {
                    // Darken the top and bottom a little so text and dock stay readable on any photo.
                    int w = getWidth(), h = getHeight();
                    shade.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                            new int[]{0x73000000, 0x00000000, 0x00000000, 0x8C000000},
                            new float[]{0f, 0.38f, 0.62f, 1f}, android.graphics.Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, shade);
                }
                super.dispatchDraw(c);
            }
        };
        root.setWillNotDraw(false);
        buildClean();
        setContentView(root);
        grid.requestFocus();
    }

    /** Clean layout: clock on the wallpaper, compact prayer card, a single glass dock. */
    private void buildClean() {
        FrameLayout stage = new FrameLayout(this);
        stage.setClipChildren(false);
        content = stage;
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        // --- top left: big clock, weather, date (no card: it floats on the photo)
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        time = text(72, 255, false);
        time.setShadowLayer(dp(14), 0, dp(2), 0x99000000);
        weatherLine = text(19, 245, true);
        weatherLine.setPadding(dp(2), dp(10), 0, 0);
        weatherLine.setShadowLayer(dp(8), 0, dp(1), 0x99000000);
        date = text(15, 225, false);
        date.setPadding(dp(2), dp(8), 0, 0);
        hijri = text(13, 190, false);
        hijri.setPadding(dp(2), dp(4), 0, 0);
        info.addView(time);
        info.addView(weatherLine);
        info.addView(date);
        info.addView(hijri);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        ilp.setMargins(dp(48), dp(24), 0, 0);
        stage.addView(info, ilp);

        // --- top right: small glass buttons
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setClipChildren(false);
        addButtons(bar);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        blp.setMargins(0, dp(28), dp(48), 0);
        stage.addView(bar, blp);

        // --- right: compact prayer card
        GlassFrame card = buildPrayerCardCompact();
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(220), -2, Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(84), dp(48), 0);
        stage.addView(card, clp);

        // --- bottom: one glass dock of apps (Down scrolls to the next row)
        GlassFrame dock = new GlassFrame(this, 24, 1f);
        dock.setClipChildren(true);
        makeGrid(dp(14), dp(16), dp(40));
        dock.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, dp(98), Gravity.BOTTOM);
        dlp.setMargins(dp(40), 0, dp(40), dp(22));
        stage.addView(dock, dlp);
    }

    /** The glass buttons: Inputs, Google Home, Style, Settings. */
    private void addButtons(LinearLayout bar) {
        inputsPill = pillSmall("Inputs");
        inputsPill.setOnClickListener(v -> showInputs());
        inputsPill.setVisibility(passthroughInputs().isEmpty() ? View.GONE : View.VISIBLE);
        bar.addView(inputsPill, pillParams());

        GlassFrame home = pillSmall("Google Home");
        home.setOnClickListener(v -> openGoogleHome());
        bar.addView(home, pillParams());

        GlassFrame style = pillSmall("Style");
        styleLabel = (TextView) style.getChildAt(0);
        style.setOnClickListener(v -> cycleTheme());
        bar.addView(style, pillParams());

        GlassFrame settings = pillSmall("Settings");
        settings.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        bar.addView(settings, pillParams());
    }

    /** The app grid inside the dock. */
    private void makeGrid(int spacing, int padding, int rowSpacing) {
        grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(spacing);
        grid.setVerticalSpacing(rowSpacing);
        grid.setPadding(padding, padding, padding, padding);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setCacheColorHint(Color.TRANSPARENT);
        // Glass depends on each tile's live position, so never draw tiles from stale bitmap caches.
        grid.setScrollingCacheEnabled(false);
        grid.setAnimationCacheEnabled(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { select(grid.hasFocus() ? v : null); }
            @Override public void onNothingSelected(AdapterView<?> p) { select(null); }
        });
        grid.setOnFocusChangeListener((v, has) -> select(has ? grid.getSelectedView() : null));
        grid.setOnItemClickListener((p, v, pos, id) -> open(apps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            options(apps.get(pos));
            return true;
        });
        // The glass behind each tile depends on its position: refresh while scrolling.
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) {}
            @Override public void onScroll(AbsListView v, int first, int count, int total) { GlassFrame.invalidateTree(v); }
        });
    }

    /**
     * One line, ellipsized. Deliberately not setSingleLine(): that lays text out
     * on a 1M-px-wide line and centres it by scrolling, which can leave recycled
     * grid tiles with their label scrolled off-screen.
     */
    private static void oneLine(TextView t) {
        t.setMaxLines(1);
        t.setHorizontallyScrolling(false);
        if (t.getEllipsize() == null) t.setEllipsize(TextUtils.TruncateAt.END);
    }

    private GlassFrame pill(String label) {
        GlassFrame f = new GlassFrame(this, 24, 1.08f);
        f.setFocusable(true);
        f.setClickable(true);
        TextView t = text(16, 240, true);
        t.setText(label);
        t.setPadding(dp(22), dp(11), dp(22), dp(11));
        f.addView(t);
        return f;
    }

    private GlassFrame pillSmall(String label) {
        GlassFrame f = new GlassFrame(this, 18, 1.08f);
        f.setFocusable(true);
        f.setClickable(true);
        TextView t = text(13, 240, true);
        t.setText(label);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        f.addView(t);
        return f;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        return lp;
    }

    private void select(View v) {
        GlassFrame next = v instanceof GlassFrame ? (GlassFrame) v : null;
        if (next == selectedTile) return;
        if (selectedTile != null) selectedTile.setHighlighted(false);
        selectedTile = next;
        if (next != null) next.setHighlighted(true);
    }

    private void updateClock() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE, d MMMM yyyy", now));
        if (weatherLine != null) {
            String w = weather.line();
            weatherLine.setText(w);
            weatherLine.setVisibility(w == null ? View.GONE : View.VISIBLE);
            if (weather.stale()) refreshWeather();
        }
    }

    private boolean weatherBusy;

    private void refreshWeather() {
        if (weatherBusy) return;
        weatherBusy = true;
        worker.execute(() -> {
            weather.refresh(prayer.location(), prayer.malay());
            ui.post(() -> {
                weatherBusy = false;
                String w = weather.line();
                if (weatherLine != null && w != null) {
                    weatherLine.setText(w);
                    weatherLine.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    // --------------------------------------------------------------- prayer --

    /** Clean layout: a narrow glass card with the six times as rows and the next prayer below. */
    private GlassFrame buildPrayerCardCompact() {
        GlassFrame card = new GlassFrame(this, 20, 1f);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.setClipChildren(false);
        card.addView(body, new FrameLayout.LayoutParams(-1, -2));

        prayerSource = text(11, 200, true);
        oneLine(prayerSource);
        prayerSource.setPadding(dp(4), 0, 0, dp(8));
        body.addView(prayerSource);

        for (int i = 0; i < 6; i++) {
            GlassFrame chip = new GlassFrame(this, 10, 1f);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(9), dp(4), dp(9), dp(4));
            chipName[i] = text(12, 225, false);
            oneLine(chipName[i]);
            chipTime[i] = text(12, 245, true);
            oneLine(chipTime[i]);
            row.addView(chipName[i], new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(chipTime[i], new LinearLayout.LayoutParams(-2, -2));
            chip.addView(row, new FrameLayout.LayoutParams(-1, -2));
            chips[i] = chip;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(i == 0 ? 0 : 3);
            body.addView(chip, lp);
        }

        // Next prayer: "Seterusnya: Zohor" / "1:00  dalam 4j 9m"
        nextLabel = text(12, 230, true);
        oneLine(nextLabel);
        nextLabel.setPadding(dp(4), dp(10), dp(4), 0);
        body.addView(nextLabel);
        LinearLayout next = new LinearLayout(this);
        next.setOrientation(LinearLayout.HORIZONTAL);
        next.setGravity(Gravity.CENTER_VERTICAL);
        next.setPadding(dp(4), dp(4), dp(4), 0);
        nextTime = text(16, 255, true);
        nextIn = text(12, 210, false);
        nextIn.setPadding(dp(8), 0, 0, 0);
        oneLine(nextIn);
        next.addView(nextTime);
        next.addView(nextIn);
        body.addView(next);

        prayerHint = text(13, 220, false);
        prayerHint.setGravity(Gravity.CENTER);
        prayerHint.setPadding(dp(14), dp(14), dp(14), dp(14));
        prayerHint.setText("Prayer times: set your location from your phone (gtv → p)");
        card.addView(prayerHint, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        prayerBody = body;
        return card;
    }

    /** Recomputes the day's times (and downloads JAKIM data when needed) off the UI thread. */
    private void refreshPrayer() {
        worker.execute(() -> {
            prayer.refresh();
            ui.post(() -> {
                todayKey = -1;
                updatePrayerCard();
                if (weatherLine != null) refreshWeather();
            });
        });
    }

    private void updatePrayerCard() {
        if (nextLabel == null) return;
        Calendar now = Calendar.getInstance();
        int key = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        if (key != todayKey) {
            todayKey = key;
            today = prayer.day(now);
            Calendar tm = (Calendar) now.clone();
            tm.add(Calendar.DAY_OF_MONTH, 1);
            tomorrow = prayer.day(tm);
        }
        boolean has = today != null;
        prayerBody.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        prayerHint.setVisibility(has ? View.GONE : View.VISIBLE);
        hijri.setVisibility(has && today.hijri != null ? View.VISIBLE : View.GONE);
        if (!has) return;
        hijri.setText(today.hijri);

        String[] names = today.malay
                ? new String[]{"Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"}
                : new String[]{"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int next = -1;
        for (int i = 0; i < 6; i++) if (today.minutes[i] > nowMin) { next = i; break; }
        // A prayer that started in the last 15 minutes is shown as "now".
        int current = -1;
        for (int i = 0; i < 6; i++) {
            if (i != PrayerCalc.SUNRISE && nowMin >= today.minutes[i] && nowMin - today.minutes[i] < 15) current = i;
        }

        for (int i = 0; i < 6; i++) {
            chipName[i].setText(names[i]);
            chipTime[i].setText(fmtShort(today.minutes[i]));
            chips[i].setHighlighted(i == (current >= 0 ? current : next));
        }

        if (current >= 0) {
            nextLabel.setText(today.malay ? "WAKTU SEKARANG" : "IT'S TIME FOR");
            nextTime.setText(names[current]);
            nextIn.setText(fmt(today.minutes[current]));
        } else {
            int target = next >= 0 ? today.minutes[next] : (tomorrow != null ? tomorrow.minutes[0] : -1);
            String name = next >= 0 ? names[next] : names[0];
            nextLabel.setText((today.malay ? "SETERUSNYA · " : "NEXT · ") + name.toUpperCase(Locale.getDefault()));
            nextTime.setText(target >= 0 ? fmt(target) : "--");
            int left = next >= 0 ? target - nowMin : target + 24 * 60 - nowMin;
            nextIn.setText((today.malay ? "dalam " : "in ") + (left >= 60 ? left / 60 + (today.malay ? "j " : "h ") : "") + left % 60 + "m");
        }
        String place = prayer.placeLabel();
        prayerSource.setText((today.malay ? "Waktu Solat" : "Prayer times") + (place.isEmpty() ? "" : "  ·  " + place));
        if (current < 0) {
            int target = next >= 0 ? today.minutes[next] : (tomorrow != null ? tomorrow.minutes[0] : -1);
            nextLabel.setText((today.malay ? "Seterusnya: " : "Next: ") + (next >= 0 ? names[next] : names[0]));
            nextTime.setText(target >= 0 ? fmtShort(target) : "--");
        }
    }

    /** "5:48" / "17:48": compact form for the small chips. */
    private String fmtShort(int minutes) {
        int h = (minutes / 60) % 24, m = minutes % 60;
        if (!DateFormat.is24HourFormat(this)) h = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.getDefault(), DateFormat.is24HourFormat(this) ? "%02d:%02d" : "%d:%02d", h, m);
    }

    private String fmt(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (minutes / 60) % 24);
        c.set(Calendar.MINUTE, minutes % 60);
        return DateFormat.getTimeFormat(this).format(c.getTime());
    }

    // ---------------------------------------------------------- google home --

    /**
     * Opens Google Home controls: the TV's Home panel if gtv.sh found one,
     * otherwise Google Assistant, which controls every Google Home device by voice.
     */
    private void openGoogleHome() {
        String panel = prefs.getString("homePanel", null);
        if (panel != null) {
            ComponentName cn = ComponentName.unflattenFromString(panel);
            if (cn != null && tryStart(new Intent().setComponent(cn))) return;
        }
        Intent[] assistant = {
                new Intent("android.intent.action.VOICE_ASSIST"),
                new Intent(Intent.ACTION_ASSIST),
                new Intent(Intent.ACTION_VOICE_COMMAND),
        };
        for (Intent i : assistant) {
            if (tryStart(i)) {
                Toast.makeText(this, "Say: \"Turn off the living room lights\"", Toast.LENGTH_LONG).show();
                return;
            }
        }
        Toast.makeText(this, "Hold the HOME button on your remote to open the Google Home panel",
                Toast.LENGTH_LONG).show();
    }

    private boolean tryStart(Intent i) {
        if (i.resolveActivity(getPackageManager()) == null) return false;
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    // --------------------------------------------------------------- themes --

    /** Saved theme, but switch to "Photo" when a new wallpaper has been pushed. */
    private int pickTheme() {
        int t = prefs.getInt("theme", 0);
        File wp = Glass.findWallpaper(this);
        long stamp = wp == null ? 0 : wp.lastModified();
        if (stamp != prefs.getLong("wallpaperStamp", 0)) {
            prefs.edit().putLong("wallpaperStamp", stamp).putInt("theme", wp != null ? 4 : 0).apply();
            t = wp != null ? 4 : 0;
        }
        return Math.max(0, Math.min(t, Glass.THEMES.length - 1));
    }

    private void cycleTheme() {
        int next = (Glass.theme + 1) % Glass.THEMES.length;
        if (Glass.THEMES[next].equals("Photo") && Glass.findWallpaper(this) == null) next = 0;
        prefs.edit().putInt("theme", next).apply();
        loadTheme(next);
    }

    private void loadTheme(int t) {
        worker.execute(() -> {
            Glass.load(this, t);
            ui.post(() -> {
                styleLabel.setText("Style · " + Glass.THEMES[Glass.theme]);
                GlassFrame.invalidateTree(root);
            });
        });
    }

    // ----------------------------------------------------------------- apps --

    private static final class App {
        String label;
        ComponentName component;
        Drawable banner, icon;
        int launches;
        boolean suggested;   // not installed: selecting it opens the Play Store
        int color;           // brand colour for the placeholder tile
    }

    private void loadApps() {
        worker.execute(() -> {
            PackageManager pm = getPackageManager();
            Map<String, App> byPkg = new HashMap<>();
            collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPkg);
            collect(pm, Intent.CATEGORY_LAUNCHER, byPkg); // phone apps without a TV entry
            byPkg.remove(getPackageName());
            List<App> list = new ArrayList<>(byPkg.values());
            // Apps hidden from Home (long-press > Hide).
            List<App> visible = new ArrayList<>();
            for (App a : list) {
                if (!prefs.getBoolean("hide:" + a.component.getPackageName(), false)) visible.add(a);
            }
            list = visible;
            for (App a : list) a.launches = prefs.getInt("n:" + a.component.getPackageName(), 0);
            // Your own arrangement (long-press > Move) first; new apps after, most used first.
            final List<String> order = savedOrder();
            Collections.sort(list, (x, y) -> {
                int ix = order.indexOf(x.component.getPackageName());
                int iy = order.indexOf(y.component.getPackageName());
                if (ix >= 0 || iy >= 0) {
                    if (ix < 0) return 1;
                    if (iy < 0) return -1;
                    return ix - iy;
                }
                return x.launches != y.launches ? y.launches - x.launches : x.label.compareToIgnoreCase(y.label);
            });
            if (!prefs.getBoolean("suggestOff", false)) {
                for (String[] s : SUGGESTED) {
                    if (byPkg.containsKey(s[0]) || prefs.getBoolean("hide:" + s[0], false)) continue;
                    list.add(suggestion(pm, s));
                }
            }
            final List<App> result = list;
            ui.post(() -> {
                apps.clear();
                apps.addAll(result);
                adapter.notifyDataSetChanged();
                if (inputsPill != null) {
                    inputsPill.setVisibility(passthroughInputs().isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        });
    }

    private App suggestion(PackageManager pm, String[] s) {
        App a = new App();
        a.suggested = true;
        a.label = s[1];
        a.color = Color.parseColor(s[2]);
        a.component = new ComponentName(s[0], "");
        // Removed by debloat (still on the system partition)? Use its real artwork.
        try {
            @SuppressWarnings("deprecation")
            int flag = Build.VERSION.SDK_INT >= 24 ? PackageManager.MATCH_UNINSTALLED_PACKAGES
                    : PackageManager.GET_UNINSTALLED_PACKAGES;
            ApplicationInfo ai = pm.getApplicationInfo(s[0], flag);
            a.banner = ai.loadBanner(pm);
            if (a.banner == null) a.icon = ai.loadIcon(pm);
        } catch (Exception ignored) {
            // Never installed: a glass tile with the app's name and colour.
        }
        return a;
    }

    private void collect(PackageManager pm, String category, Map<String, App> out) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            ActivityInfo ai = r.activityInfo;
            if (out.containsKey(ai.packageName)) continue;
            App a = new App();
            a.component = new ComponentName(ai.packageName, ai.name);
            CharSequence l = r.loadLabel(pm);
            a.label = l == null ? ai.packageName : l.toString();
            a.banner = ai.loadBanner(pm);
            if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
            if (a.banner == null) a.icon = r.loadIcon(pm);
            out.put(ai.packageName, a);
        }
    }

    private void open(App a) {
        String pkg = a.component.getPackageName();
        if (a.suggested) {
            // Install only when asked: show the app's Play Store page.
            if (!tryStart(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)))) {
                tryStart(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            }
            return;
        }
        String key = "n:" + pkg;
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
        launch(new Intent(Intent.ACTION_MAIN).setComponent(a.component)
                .addCategory(Intent.CATEGORY_LAUNCHER));
    }

    /** Long-press OK / MENU on an app: a glass menu of what you can do with it. */
    private void options(App a) {
        final String pkg = a.component.getPackageName();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (a.suggested) {
            labels.add("Get on Play Store");
            actions.add(() -> open(a));
            labels.add("Hide this suggestion");
            actions.add(() -> hide(a));
        } else {
            labels.add("Open");
            actions.add(() -> open(a));
            labels.add("Move");
            actions.add(this::startMoving);
            labels.add("Hide from Home");
            actions.add(() -> hide(a));
            labels.add("App info");
            actions.add(() -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg))));
        }
        int hidden = hiddenCount();
        if (hidden > 0) {
            labels.add("Show hidden apps (" + hidden + ")");
            actions.add(this::unhideAll);
        }
        showMenu(a.label, null, labels, actions);
    }

    private void hide(App a) {
        prefs.edit().putBoolean("hide:" + a.component.getPackageName(), true).apply();
        Toast.makeText(this, a.label + " hidden. Long-press any app to show it again.", Toast.LENGTH_LONG).show();
        loadApps();
    }

    private int hiddenCount() {
        int n = 0;
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getKey().startsWith("hide:") && Boolean.TRUE.equals(e.getValue())) n++;
        }
        return n;
    }

    private void unhideAll() {
        SharedPreferences.Editor ed = prefs.edit();
        for (String k : prefs.getAll().keySet()) if (k.startsWith("hide:")) ed.remove(k);
        ed.apply();
        loadApps();
    }

    // ------------------------------------------------------------- move mode --

    private List<String> savedOrder() {
        String s = prefs.getString("order", "");
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (App a : apps) {
            if (a.suggested) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(a.component.getPackageName());
        }
        prefs.edit().putString("order", sb.toString()).apply();
    }

    private int installedCount() {
        int n = 0;
        for (App a : apps) if (!a.suggested) n++;
        return n;
    }

    private void startMoving() {
        int pos = grid.getSelectedItemPosition();
        if (pos < 0 || apps.get(pos).suggested) return;
        moving = true;
        grid.requestFocus();
        Toast.makeText(this, "Use the arrows to move it, then press OK", Toast.LENGTH_LONG).show();
        startWiggle();
    }

    private void stopMoving() {
        moving = false;
        if (wiggle != null) wiggle.cancel();
        View v = grid.getSelectedView();
        if (v != null) v.setRotation(0);
        saveOrder();
    }

    private void startWiggle() {
        if (wiggle != null) wiggle.cancel();
        for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).setRotation(0);
        View v = grid.getSelectedView();
        if (v == null) return;
        wiggle = ObjectAnimator.ofFloat(v, View.ROTATION, -1.6f, 1.6f);
        wiggle.setDuration(130);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.start();
    }

    /** Arrows carry the app; OK or Back puts it down. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!moving || e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int pos = grid.getSelectedItemPosition();
        int target = pos;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: target = pos - 1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: target = pos + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: target = pos - COLUMNS; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: target = pos + COLUMNS; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                stopMoving();
                return true;
            default:
                return true; // ignore other keys while moving
        }
        if (target >= 0 && target < installedCount() && target != pos) {
            App a = apps.remove(pos);
            apps.add(target, a);
            adapter.notifyDataSetChanged();
            grid.setSelection(target);
            ui.post(this::startWiggle);
        }
        return true;
    }

    // ---------------------------------------------------------------- inputs --

    /** HDMI / AV inputs the TV exposes (empty on sticks and boxes). */
    private List<TvInputInfo> passthroughInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 21) return out;
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && !info.isHidden(this)) out.add(info);
            }
        } catch (RuntimeException ignored) {
            // No TV input framework on this device.
        }
        return out;
    }

    private void showInputs() {
        final List<TvInputInfo> inputs = passthroughInputs();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (final TvInputInfo info : inputs) {
            CharSequence custom = Build.VERSION.SDK_INT >= 24 ? info.loadCustomLabel(this) : null;
            CharSequence label = custom != null && custom.length() > 0 ? custom : info.loadLabel(this);
            labels.add(label == null ? info.getId() : label.toString());
            actions.add(() -> {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()));
                if (!tryStart(i)) {
                    Toast.makeText(this, "No app on this TV can show inputs. If you removed the TV's Live TV app, "
                            + "bring it back from your phone with: gtv bring-back", Toast.LENGTH_LONG).show();
                }
            });
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "This device has no HDMI inputs", Toast.LENGTH_SHORT).show();
            return;
        }
        showMenu("Inputs", null, labels, actions);
    }

    // ------------------------------------------------------------- glass menu --

    /** A frosted-glass list of choices over a dimmed Home; Back closes it. */
    private void showMenu(String title, String subtitle, List<String> labels, List<Runnable> actions) {
        closeMenu();
        focusBeforeMenu = getCurrentFocus();
        menuLayer = new FrameLayout(this);
        menuLayer.setBackgroundColor(0x80000000);
        menuLayer.setClickable(true);

        GlassFrame panel = new GlassFrame(this, 26, 1f);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(18), dp(22), dp(18));
        col.setClipChildren(false);
        col.setClipToPadding(false);
        panel.setClipChildren(false);
        TextView t = text(20, 255, true);
        t.setText(title);
        t.setPadding(dp(6), 0, 0, dp(subtitle == null ? 12 : 4));
        oneLine(t);
        col.addView(t);
        if (subtitle != null) {
            TextView st = text(13, 190, false);
            st.setText(subtitle);
            st.setPadding(dp(6), 0, 0, dp(12));
            col.addView(st);
        }
        GlassFrame first = null;
        for (int i = 0; i < labels.size(); i++) {
            final Runnable action = actions.get(i);
            GlassFrame item = pill(labels.get(i));
            item.setOnClickListener(v -> {
                closeMenu();
                action.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            col.addView(item, lp);
            if (first == null) first = item;
        }
        panel.addView(col, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(360), -2, Gravity.CENTER);
        menuLayer.addView(panel, plp);
        root.addView(menuLayer, new FrameLayout.LayoutParams(-1, -1));
        // Keep the remote's focus inside the menu: nothing behind it can be selected.
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (first != null) first.requestFocus();
    }

    private void closeMenu() {
        if (menuLayer == null) return;
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        root.removeView(menuLayer);
        menuLayer = null;
        if (focusBeforeMenu != null && focusBeforeMenu.isAttachedToWindow()) focusBeforeMenu.requestFocus();
        else grid.requestFocus();
        focusBeforeMenu = null;
    }

    private void launch(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // App disappeared or is disabled; the package receiver will refresh the grid.
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU && menuLayer != null) {
            closeMenu();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && grid.hasFocus() && grid.getSelectedItemPosition() >= 0) {
            options(apps.get(grid.getSelectedItemPosition()));
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    // -------------------------------------------------------------- adapter --

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int p) { return apps.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Tile tile = convert instanceof Tile ? (Tile) convert : new Tile();
            int colW = grid.getColumnWidth();
            int h = colW > 0 ? colW * 9 / 16 : dp(96);
            tile.setLayoutParams(new AbsListView.LayoutParams(-1, h));
            tile.bind(apps.get(pos));
            // Views are recycled: keep the glow on whichever view shows the selected app.
            if (grid.hasFocus() && pos == grid.getSelectedItemPosition()) select(tile);
            else if (tile == selectedTile) select(null);
            return tile;
        }
    }

    /** One app: TV banner inset in glass, icon + name, or a "Get" placeholder. */
    private final class Tile extends GlassFrame {
        final ImageView banner = new ImageView(HomeActivity.this);
        final LinearLayout iconBox = new LinearLayout(HomeActivity.this);
        final FrameLayout iconFrame = new FrameLayout(HomeActivity.this);
        final ImageView icon = new ImageView(HomeActivity.this);
        final TextView initial = text(22, 255, true);
        final TextView label = text(13, 235, true);
        final TextView badge = text(11, 255, true);
        final GradientDrawable dot = new GradientDrawable();

        Tile() {
            super(HomeActivity.this, 12, 1.1f);
            final float r = dp(8);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            banner.setClipToOutline(true);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -1);
            int inset = dp(3);
            blp.setMargins(inset, inset, inset, inset);
            addView(banner, blp);

            iconBox.setOrientation(LinearLayout.VERTICAL);
            iconBox.setGravity(Gravity.CENTER);
            iconFrame.addView(icon, new FrameLayout.LayoutParams(-1, -1));
            initial.setGravity(Gravity.CENTER);
            iconFrame.addView(initial, new FrameLayout.LayoutParams(-1, -1));
            int iconSize = dp(28);
            iconBox.addView(iconFrame, new LinearLayout.LayoutParams(iconSize, iconSize));
            label.setGravity(Gravity.CENTER);
            oneLine(label);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setPadding(dp(6), dp(4), dp(6), 0);
            initial.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            iconBox.addView(label, new LinearLayout.LayoutParams(-1, -2));
            addView(iconBox, new FrameLayout.LayoutParams(-1, -1));

            dot.setShape(GradientDrawable.OVAL);

            // Small glass "Get" pill for apps that aren't installed.
            badge.setText("Get");
            badge.setPadding(dp(6), dp(1), dp(6), dp(1));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(dp(10));
            pillBg.setColor(0x55FFFFFF);
            pillBg.setStroke(Math.max(1, dp(1)), 0x99FFFFFF);
            badge.setBackground(pillBg);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            bl.setMargins(0, dp(4), dp(4), 0);
            addView(badge, bl);
        }

        void bind(App a) {
            boolean hasBanner = a.banner != null;
            banner.setVisibility(hasBanner ? VISIBLE : GONE);
            iconBox.setVisibility(hasBanner ? GONE : VISIBLE);
            badge.setVisibility(a.suggested ? VISIBLE : GONE);
            float alpha = a.suggested ? 0.6f : 1f;
            banner.setAlpha(alpha);
            iconFrame.setAlpha(alpha);
            if (hasBanner) {
                banner.setImageDrawable(a.banner);
            } else if (a.icon != null) {
                icon.setImageDrawable(a.icon);
                initial.setVisibility(GONE);
                label.setText(a.label);
            } else {
                // Placeholder: brand-coloured circle with the app's initial.
                dot.setColor(a.color);
                icon.setImageDrawable(dot);
                initial.setVisibility(VISIBLE);
                initial.setText(a.label.substring(0, 1));
                label.setText(a.label);
            }
            setContentDescription(a.suggested ? a.label + " (not installed)" : a.label);
        }
    }
}
