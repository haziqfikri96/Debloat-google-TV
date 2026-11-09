package io.github.debloatgtv.glass;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Floating glass "now playing" card: Spotify (or whatever app is playing music)
 * with artwork, title, progress and previous / play-pause / next.
 * Hidden when nothing is playing, or when media access was not granted
 * (`gtv glass` grants it).
 */
class NowPlaying extends GlassFrame {

    private static final String[] PREFERRED = {"com.spotify.tv.android", "com.spotify.music"};

    /** Told when the card hides while the remote's focus was on it. */
    interface Listener { void onHiddenWithFocus(); }

    private final Context ctx;
    private final float d;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ImageView art;
    private final TextView source, title, artist, times;
    private final Bar bar;
    private final IconButton prev, play, next;
    private MediaSessionManager msm;
    private MediaController controller;
    private Listener listener;
    private boolean started, shown;

    // What is shown (also set directly by previews/tests through show()).
    private long position, duration, updatedAt;
    private float speed;
    private boolean playing;

    NowPlaying(Context c) {
        super(c, 22, 1f);
        ctx = c;
        d = c.getResources().getDisplayMetrics().density;
        setClipChildren(false);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(14), dp(14), dp(10));
        col.setClipChildren(false);
        col.setClipToPadding(false);

        // artwork + text
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10));
            }
        });
        art.setClipToOutline(true);
        art.setBackgroundColor(0x33FFFFFF);
        top.addView(art, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12), 0, 0, 0);
        source = text(11, 0xFF5FE08F, true);
        source.setLetterSpacing(0.08f);
        title = text(16, 0xFFFFFFFF, true);
        artist = text(13, 0xCCFFFFFF, false);
        title.setPadding(0, dp(4), 0, dp(3));
        words.addView(source);
        words.addView(title);
        words.addView(artist);
        top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(top);

        // progress
        bar = new Bar(c);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(4));
        blp.topMargin = dp(12);
        col.addView(bar, blp);
        times = text(11, 0xB3FFFFFF, false);
        times.setGravity(Gravity.END);
        times.setPadding(0, dp(5), 0, 0);
        col.addView(times, new LinearLayout.LayoutParams(-1, -2));

        // controls
        LinearLayout controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setClipChildren(false);
        prev = new IconButton(c, Icon.PREV, null, 40);
        play = new IconButton(c, Icon.PAUSE, null, 48);
        next = new IconButton(c, Icon.NEXT, null, 40);
        prev.setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToPrevious(); });
        next.setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToNext(); });
        play.setOnClickListener(v -> togglePlay());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.leftMargin = dp(10);
        sp.rightMargin = dp(10);
        controls.addView(prev, sp);
        controls.addView(play, new LinearLayout.LayoutParams(sp));
        controls.addView(next, new LinearLayout.LayoutParams(sp));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(2);
        col.addView(controls, clp);

        // Long-press OK on any control opens the app.
        for (View v : new View[]{prev, play, next}) v.setOnLongClickListener(x -> { openApp(); return true; });

        addView(col, new LayoutParams(-1, -2));
        setVisibility(GONE);
        setAlpha(0f);
    }

    void setListener(Listener l) { listener = l; }

    private int dp(float v) { return Math.round(v * d); }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setShadowLayer(4 * d, 0, d, 0x55000000);
        t.setIncludeFontPadding(false);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    // ------------------------------------------------------------ sessions --

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> pick(list);

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { bindController(); }
        @Override public void onMetadataChanged(MediaMetadata m) { bindController(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };

    /** Start following media sessions (Home is visible). */
    void start() {
        started = true;
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(ctx, MediaListener.class);
            msm.addOnActiveSessionsChangedListener(sessions, cn, ui);
            pick(msm.getActiveSessions(cn));
        } catch (SecurityException | NullPointerException e) {
            msm = null; // media access not granted: stay hidden
            setController(null);
        }
        ui.post(tick);
    }

    void stop() {
        started = false;
        ui.removeCallbacks(tick);
        if (msm != null) {
            try { msm.removeOnActiveSessionsChangedListener(sessions); } catch (RuntimeException ignored) {}
        }
        setController(null, false);
    }

    private void refresh() {
        if (msm == null) { setController(null); return; }
        try {
            pick(msm.getActiveSessions(new ComponentName(ctx, MediaListener.class)));
        } catch (SecurityException e) {
            setController(null);
        }
    }

    /** Spotify first, then anything playing, then a paused Spotify. */
    private void pick(List<MediaController> list) {
        MediaController best = null;
        int bestScore = -1;
        if (list != null) {
            for (MediaController mc : list) {
                MediaMetadata m = mc.getMetadata();
                if (m == null || TextUtils.isEmpty(m.getString(MediaMetadata.METADATA_KEY_TITLE))) continue;
                PlaybackState s = mc.getPlaybackState();
                boolean isPlaying = s != null && s.getState() == PlaybackState.STATE_PLAYING;
                boolean spotify = preferred(mc.getPackageName());
                int score = (isPlaying ? 2 : 0) + (spotify ? 1 : 0);
                if (!isPlaying && !spotify) continue; // paused video apps etc. stay out of the way
                if (score > bestScore) { best = mc; bestScore = score; }
            }
        }
        setController(best);
    }

    private static boolean preferred(String pkg) {
        for (String p : PREFERRED) if (p.equals(pkg)) return true;
        return false;
    }

    private void setController(MediaController mc) { setController(mc, true); }

    private void setController(MediaController mc, boolean animate) {
        if (controller != null && (mc == null || !controller.getSessionToken().equals(mc.getSessionToken()))) {
            controller.unregisterCallback(callback);
        }
        boolean changed = controller == null || mc == null || !controller.getSessionToken().equals(mc.getSessionToken());
        controller = mc;
        if (mc != null && changed) mc.registerCallback(callback, ui);
        if (mc == null) { setShown(false, animate); return; }
        bindController();
    }

    private void bindController() {
        MediaController mc = controller;
        if (mc == null) return;
        MediaMetadata m = mc.getMetadata();
        PlaybackState s = mc.getPlaybackState();
        if (m == null) { setShown(false, true); return; }
        Bitmap bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        String who = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(who)) who = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(who)) who = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        boolean isPlaying = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        show(appName(mc.getPackageName()), m.getString(MediaMetadata.METADATA_KEY_TITLE), who,
                bmp, appIcon(mc.getPackageName()), isPlaying,
                s == null ? 0 : s.getPosition(), m.getLong(MediaMetadata.METADATA_KEY_DURATION),
                s == null ? 0 : s.getLastPositionUpdateTime(), s == null ? 0 : s.getPlaybackSpeed());
    }

    /** Fills the card. Package-private so previews can show sample music. */
    void show(String app, String songTitle, String songArtist, Bitmap artwork, Drawable fallback,
              boolean isPlaying, long pos, long dur, long updated, float spd) {
        source.setText(app == null ? "" : app.toUpperCase(java.util.Locale.ROOT));
        title.setText(songTitle);
        artist.setText(songArtist == null ? "" : songArtist);
        artist.setVisibility(TextUtils.isEmpty(songArtist) ? GONE : VISIBLE);
        if (artwork != null) art.setImageBitmap(artwork); else art.setImageDrawable(fallback);
        playing = isPlaying;
        play.icon.setKind(isPlaying ? Icon.PAUSE : Icon.PLAY);
        position = pos;
        duration = dur;
        updatedAt = updated;
        speed = spd;
        updateProgress();
        setShown(true, true);
    }

    private void setShown(boolean on, boolean animate) {
        if (on == shown) return;
        shown = on;
        animate().cancel();
        if (on) {
            setVisibility(VISIBLE);
            if (animate) {
                setTranslationY(dp(10));
                animate().alpha(1f).translationY(0).setDuration(220).start();
            } else {
                setAlpha(1f);
            }
        } else {
            boolean hadFocus = hasFocus();
            if (animate) animate().alpha(0f).setDuration(180).withEndAction(() -> { if (!shown) setVisibility(GONE); }).start();
            else { setAlpha(0f); setVisibility(GONE); }
            if (hadFocus && listener != null) listener.onHiddenWithFocus();
        }
    }

    private void togglePlay() {
        if (controller == null) return;
        if (playing) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    private void openApp() {
        MediaController mc = controller;
        if (mc == null) return;
        PendingIntent pi = mc.getSessionActivity();
        try {
            if (pi != null) { pi.send(); return; }
        } catch (PendingIntent.CanceledException ignored) {}
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(mc.getPackageName());
        if (i == null) i = pm.getLaunchIntentForPackage(mc.getPackageName());
        if (i != null) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (RuntimeException ignored) {}
        }
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ progress --

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (shown && playing) updateProgress();
            ui.postDelayed(this, 1000);
        }
    };

    private void updateProgress() {
        long pos = position;
        if (playing && updatedAt > 0) pos += (long) ((SystemClock.elapsedRealtime() - updatedAt) * (speed == 0 ? 1 : speed));
        if (duration > 0) pos = Math.max(0, Math.min(pos, duration));
        bar.setFraction(duration > 0 ? pos / (float) duration : 0f);
        bar.setVisibility(duration > 0 ? VISIBLE : INVISIBLE);
        times.setText(duration > 0 ? clock(pos) + "  /  " + clock(duration) : "");
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    /** Thin rounded progress line. */
    private static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float f;

        Bar(Context c) {
            super(c);
            track.setColor(0x40FFFFFF);
            done.setColor(0xF2FFFFFF);
        }

        void setFraction(float v) {
            if (Math.abs(v - f) < 0.0005f) return;
            f = v;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), r = h / 2f, w = getWidth();
            c.drawRoundRect(0, 0, w, h, r, r, track);
            if (f > 0) c.drawRoundRect(0, 0, Math.max(h, w * f), h, r, r, done);
        }
    }
}
