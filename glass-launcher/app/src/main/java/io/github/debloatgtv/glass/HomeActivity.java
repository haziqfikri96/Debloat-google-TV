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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Glass Launcher: a minimal, fast Android TV home screen with a frosted-glass look. */
public class HomeActivity extends Activity {

    private static final int COLUMNS = 5;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<App> apps = new ArrayList<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private TextView time, date, styleLabel;
    private GridView grid;
    private Adapter adapter;
    private GlassFrame selectedTile;

    // ------------------------------------------------------------ lifecycle --

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("glass", MODE_PRIVATE);
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
        if (grid != null && adapter.getCount() > 0) {
            grid.setSelection(0);
            grid.requestFocus();
        }
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
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
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

        // --- top bar: clock card on the left, glass buttons on the right
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClipChildren(false);
        column.addView(top, new LinearLayout.LayoutParams(-1, -2));

        GlassFrame clock = new GlassFrame(this, 22, 1f);
        LinearLayout clockText = new LinearLayout(this);
        clockText.setOrientation(LinearLayout.VERTICAL);
        clockText.setPadding(dp(22), dp(12), dp(26), dp(12));
        time = text(40, 255, false);
        date = text(13, 200, false);
        date.setPadding(0, dp(4), 0, 0);
        clockText.addView(time);
        clockText.addView(date);
        clock.addView(clockText);
        top.addView(clock, new LinearLayout.LayoutParams(-2, -2));

        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        GlassFrame style = pill("Style");
        styleLabel = (TextView) style.getChildAt(0);
        style.setOnClickListener(v -> cycleTheme());
        top.addView(style, pillParams());

        GlassFrame settings = pill("Settings");
        settings.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        top.addView(settings, pillParams());

        // --- apps panel: one big glass sheet holding a grid of glass tiles
        TextView title = text(15, 190, true);
        title.setText("Apps");
        title.setLetterSpacing(0.08f);
        title.setPadding(dp(6), dp(26), 0, dp(10));
        column.addView(title);

        GlassFrame sheet = new GlassFrame(this, 28, 1f);
        sheet.setClipChildren(false);
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
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { select(grid.hasFocus() ? v : null); }
            @Override public void onNothingSelected(AdapterView<?> p) { select(null); }
        });
        grid.setOnFocusChangeListener((v, has) -> select(has ? grid.getSelectedView() : null));
        grid.setOnItemClickListener((p, v, pos, id) -> open(apps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + apps.get(pos).component.getPackageName())));
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

    private GlassFrame pill(String label) {
        GlassFrame f = new GlassFrame(this, 24, 1.08f);
        f.setFocusable(true);
        f.setClickable(true);
        TextView t = text(16, 240, true);
        t.setText(label);
        t.setPadding(dp(24), dp(12), dp(24), dp(12));
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
            ui.post(() -> {
                apps.clear();
                apps.addAll(list);
                adapter.notifyDataSetChanged();
            });
        });
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
        String key = "n:" + a.component.getPackageName();
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
        launch(new Intent(Intent.ACTION_MAIN).setComponent(a.component)
                .addCategory(Intent.CATEGORY_LAUNCHER));
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
        // MENU on the remote opens app info for the selected tile.
        if (keyCode == KeyEvent.KEYCODE_MENU && grid.hasFocus() && grid.getSelectedItemPosition() >= 0) {
            grid.getOnItemLongClickListener().onItemLongClick(grid, grid.getSelectedView(),
                    grid.getSelectedItemPosition(), 0);
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

    /** One app: its TV banner inset in glass, or icon + name for phone apps. */
    private final class Tile extends GlassFrame {
        final ImageView banner = new ImageView(HomeActivity.this);
        final LinearLayout iconBox = new LinearLayout(HomeActivity.this);
        final ImageView icon = new ImageView(HomeActivity.this);
        final TextView label = text(13, 235, true);

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
            iconBox.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(8), dp(8), dp(8), 0);
            iconBox.addView(label, new LinearLayout.LayoutParams(-1, -2));
            addView(iconBox, new FrameLayout.LayoutParams(-1, -1));
        }

        void bind(App a) {
            boolean hasBanner = a.banner != null;
            banner.setVisibility(hasBanner ? VISIBLE : GONE);
            iconBox.setVisibility(hasBanner ? GONE : VISIBLE);
            if (hasBanner) banner.setImageDrawable(a.banner);
            else {
                icon.setImageDrawable(a.icon);
                label.setText(a.label);
            }
            setContentDescription(a.label);
        }
    }
}
