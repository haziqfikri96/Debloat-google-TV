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

    private static final int COLUMNS = 5;

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

    // prayer card
    private TextView nextLabel, nextTime, nextIn, prayerSource;
    private LinearLayout prayerRow, prayerBody;
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
        handleConfig(getIntent());
        buildUi();
        loadTheme(pickTheme());
        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateClock();
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(clockReceiver, time);
        IntentFilter pkg = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addDataScheme("package");
        register(packageReceiver, pkg);
        // A new wallpaper may have been pushed while we were in the background.
        int t = pickTheme();
        if (t != Glass.theme) loadTheme(t);
        refreshPrayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(clockReceiver);
        unregisterReceiver(packageReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        // We are the home screen: there is nothing to go back to.
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
        root = new FrameLayout(this) {
            @Override protected void dispatchDraw(Canvas c) {
                Glass.drawBackground(c, this);
                super.dispatchDraw(c);
            }
        };
        root.setWillNotDraw(false);

        // 5% TV overscan-safe margins.
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(48), dp(27), dp(48), 0);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        // --- top row: clock card + prayer times card
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);
        column.addView(top, new LinearLayout.LayoutParams(-1, -2));

        GlassFrame clock = new GlassFrame(this, 22, 1f);
        LinearLayout clockText = new LinearLayout(this);
        clockText.setOrientation(LinearLayout.VERTICAL);
        clockText.setGravity(Gravity.CENTER_VERTICAL);
        clockText.setPadding(dp(22), dp(12), dp(26), dp(12));
        time = text(40, 255, false);
        date = text(13, 210, false);
        date.setPadding(0, dp(6), 0, 0);
        hijri = text(13, 170, false);
        hijri.setPadding(0, dp(4), 0, 0);
        clockText.addView(time);
        clockText.addView(date);
        clockText.addView(hijri);
        clock.addView(clockText, new FrameLayout.LayoutParams(-2, -1));
        top.addView(clock, new LinearLayout.LayoutParams(-2, -1));

        GlassFrame card = buildPrayerCard();
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -1, 1f);
        cardLp.leftMargin = dp(16);
        top.addView(card, cardLp);

        // --- title row: "Apps" + glass buttons
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setPadding(0, dp(18), 0, dp(12));
        column.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(15, 190, true);
        title.setText("Apps");
        title.setLetterSpacing(0.08f);
        title.setPadding(dp(6), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        GlassFrame home = pill("Google Home");
        home.setOnClickListener(v -> openGoogleHome());
        bar.addView(home, pillParams());

        GlassFrame style = pill("Style");
        styleLabel = (TextView) style.getChildAt(0);
        style.setOnClickListener(v -> cycleTheme());
        bar.addView(style, pillParams());

        GlassFrame settings = pill("Settings");
        settings.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        bar.addView(settings, pillParams());

        // --- apps panel: one big glass sheet holding a grid of glass tiles
        GlassFrame sheet = new GlassFrame(this, 28, 1f);
        // Clip scrolled rows to the sheet; the grid's padding leaves room for the focus lift.
        sheet.setClipChildren(true);
        LinearLayout.LayoutParams sheetLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        sheetLp.bottomMargin = dp(27);
        column.addView(sheet, sheetLp);

        grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(18));
        grid.setVerticalSpacing(dp(18));
        grid.setPadding(dp(22), dp(22), dp(22), dp(22));
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
        sheet.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        grid.requestFocus();
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

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(14);
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
        time.setText(DateFormat.getTimeFormat(this).format(now));
        date.setText(DateFormat.format("EEEE, d MMMM", now).toString().toUpperCase(Locale.getDefault()));
    }

    // --------------------------------------------------------------- prayer --

    private GlassFrame buildPrayerCard() {
        GlassFrame card = new GlassFrame(this, 22, 1f);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(16), dp(10));
        card.addView(body, new FrameLayout.LayoutParams(-1, -1));

        prayerRow = new LinearLayout(this);
        prayerRow.setOrientation(LinearLayout.HORIZONTAL);
        prayerRow.setGravity(Gravity.CENTER_VERTICAL);
        prayerRow.setClipChildren(false);
        body.addView(prayerRow, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout next = new LinearLayout(this);
        next.setOrientation(LinearLayout.VERTICAL);
        nextLabel = text(11, 190, true);
        nextLabel.setLetterSpacing(0.12f);
        nextTime = text(30, 255, false);
        nextTime.setPadding(0, dp(4), 0, dp(3));
        nextIn = text(13, 210, false);
        next.addView(nextLabel);
        next.addView(nextTime);
        next.addView(nextIn);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(150), -2);
        prayerRow.addView(next, nlp);

        for (int i = 0; i < 6; i++) {
            GlassFrame chip = new GlassFrame(this, 14, 1f);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setPadding(0, dp(8), 0, dp(8));
            chipName[i] = text(11, 200, true);
            chipName[i].setGravity(Gravity.CENTER);
            oneLine(chipName[i]);
            chipTime[i] = text(17, 245, false);
            chipTime[i].setGravity(Gravity.CENTER);
            oneLine(chipTime[i]);
            chipTime[i].setPadding(0, dp(5), 0, 0);
            c.addView(chipName[i], new LinearLayout.LayoutParams(-1, -2));
            c.addView(chipTime[i], new LinearLayout.LayoutParams(-1, -2));
            chip.addView(c, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            chips[i] = chip;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = dp(8);
            prayerRow.addView(chip, lp);
        }

        prayerSource = text(11, 150, false);
        prayerSource.setPadding(0, dp(8), 0, 0);
        oneLine(prayerSource);
        prayerSource.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(prayerSource);

        prayerHint = text(15, 220, false);
        prayerHint.setGravity(Gravity.CENTER);
        prayerHint.setText("Prayer times: set your location from your phone\n(gtv menu → p)  or  ./gtv.sh prayer \"Your City\"");
        prayerHint.setLineSpacing(dp(4), 1f);
        card.addView(prayerHint, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
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
            chipName[i].setText(names[i].toUpperCase(Locale.getDefault()));
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
            nextIn.setText((today.malay ? "dalam " : "in ") + (left >= 60 ? left / 60 + "h " : "") + left % 60 + "m");
        }
        String place = prayer.placeLabel();
        prayerSource.setText((place.isEmpty() ? "" : place + "  ·  ") + today.source);
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
            for (App a : list) a.launches = prefs.getInt("n:" + a.component.getPackageName(), 0);
            // Most used first, then alphabetical.
            Collections.sort(list, (x, y) -> x.launches != y.launches
                    ? y.launches - x.launches : x.label.compareToIgnoreCase(y.label));
            if (!prefs.getBoolean("suggestOff", false)) {
                for (String[] s : SUGGESTED) {
                    if (byPkg.containsKey(s[0]) || prefs.getBoolean("hide:" + s[0], false)) continue;
                    list.add(suggestion(pm, s));
                }
            }
            ui.post(() -> {
                apps.clear();
                apps.addAll(list);
                adapter.notifyDataSetChanged();
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

    /** Long-press / MENU: App info for installed apps; hide for suggestions. */
    private void options(App a) {
        String pkg = a.component.getPackageName();
        if (a.suggested) {
            prefs.edit().putBoolean("hide:" + pkg, true).apply();
            Toast.makeText(this, a.label + " hidden", Toast.LENGTH_SHORT).show();
            loadApps();
            return;
        }
        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
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
            super(HomeActivity.this, 16, 1.1f);
            final float r = dp(11);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            banner.setClipToOutline(true);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -1);
            int inset = dp(5);
            blp.setMargins(inset, inset, inset, inset);
            addView(banner, blp);

            iconBox.setOrientation(LinearLayout.VERTICAL);
            iconBox.setGravity(Gravity.CENTER);
            iconFrame.addView(icon, new FrameLayout.LayoutParams(-1, -1));
            initial.setGravity(Gravity.CENTER);
            iconFrame.addView(initial, new FrameLayout.LayoutParams(-1, -1));
            iconBox.addView(iconFrame, new LinearLayout.LayoutParams(dp(44), dp(44)));
            label.setGravity(Gravity.CENTER);
            oneLine(label);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(8), dp(8), dp(8), 0);
            iconBox.addView(label, new LinearLayout.LayoutParams(-1, -2));
            addView(iconBox, new FrameLayout.LayoutParams(-1, -1));

            dot.setShape(GradientDrawable.OVAL);

            // Small glass "Get" pill for apps that aren't installed.
            badge.setText("Get");
            badge.setPadding(dp(9), dp(3), dp(9), dp(3));
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(dp(10));
            pillBg.setColor(0x55FFFFFF);
            pillBg.setStroke(Math.max(1, dp(1)), 0x99FFFFFF);
            badge.setBackground(pillBg);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            bl.setMargins(0, dp(9), dp(9), 0);
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
