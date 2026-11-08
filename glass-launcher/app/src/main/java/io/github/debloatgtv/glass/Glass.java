package io.github.debloatgtv.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Environment;
import android.view.View;

import java.io.File;

/**
 * Shared "frosted glass" renderer.
 *
 * Real-time blur is expensive on TV chips, so instead the background is
 * blurred ONCE into a tiny bitmap. Every glass surface then draws the slice
 * of that blurred bitmap that sits behind it (a BitmapShader offset by the
 * view's position on screen), plus a translucent tint, a top highlight and
 * a thin bright border. That is a few GPU draw calls per card: smooth even
 * on a Chromecast.
 */
final class Glass {

    static final String[] THEMES = {"Aurora", "Ocean", "Sunset", "Graphite", "Photo"};

    // Base colour + four glowing blobs per theme.
    private static final int[][] PALETTES = {
            {0xFF070B1E, 0xFF7C4DFF, 0xFF00E5FF, 0xFFFF4081, 0xFF1DE9B6},
            {0xFF021526, 0xFF0091EA, 0xFF00E5FF, 0xFF304FFE, 0xFF64FFDA},
            {0xFF1A0612, 0xFFFF6D00, 0xFFFF4081, 0xFFAA00FF, 0xFFFFD740},
            {0xFF0E0F12, 0xFF546E7A, 0xFF90A4AE, 0xFF37474F, 0xFFB0BEC5},
    };

    static Bitmap sharp;    // full background (shown behind everything)
    static Bitmap blurred;  // tiny, heavily blurred copy used inside glass
    static int theme;

    private static final Matrix MATRIX = new Matrix();
    private static final int[] LOC = new int[2];
    private static final Paint BLUR_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint TINT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BORDER_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static BitmapShader shader;

    static {
        BORDER_PAINT.setStyle(Paint.Style.STROKE);
    }

    private Glass() {}

    /** Wallpaper locations `gtv.sh wallpaper` pushes to. */
    static File[] wallpaperCandidates(Context c) {
        File ext = c.getExternalFilesDir(null);
        return new File[]{
                ext == null ? null : new File(ext, "wallpaper.jpg"),
                new File(Environment.getExternalStorageDirectory(), "Pictures/glass-wallpaper.jpg"),
        };
    }

    static File findWallpaper(Context c) {
        for (File f : wallpaperCandidates(c)) {
            if (f != null && f.canRead() && f.length() > 0) return f;
        }
        return null;
    }

    /** Builds the background for a theme. Runs off the UI thread. */
    static void load(Context c, int themeIndex) {
        Bitmap bg = null;
        if (THEMES[themeIndex].equals("Photo")) {
            File f = findWallpaper(c);
            if (f != null) bg = decodePhoto(f, 1280, 720);
            if (bg == null) themeIndex = 0;
        }
        if (bg == null) bg = generate(PALETTES[themeIndex], 960, 540);

        Bitmap small = Bitmap.createScaledBitmap(bg, 160, 90, true);
        small = small.copy(Bitmap.Config.ARGB_8888, true);
        Blur.box(small, 6, 3);

        synchronized (Glass.class) {
            sharp = bg;
            blurred = small;
            theme = themeIndex;
            shader = new BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            BLUR_PAINT.setShader(shader);
        }
    }

    /** Soft glowing blobs on a dark base: gives the glass something to refract. */
    private static Bitmap generate(int[] p, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(p[0]);
        float[][] blobs = { // x, y, radius (fractions of width)
                {0.18f, 0.25f, 0.45f},
                {0.85f, 0.80f, 0.50f},
                {0.70f, 0.10f, 0.30f},
                {0.30f, 0.95f, 0.35f},
        };
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < blobs.length; i++) {
            float cx = blobs[i][0] * w, cy = blobs[i][1] * h, r = blobs[i][2] * w;
            int col = p[i + 1];
            paint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{withAlpha(col, 0xCC), withAlpha(col, 0x55), withAlpha(col, 0)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, paint);
        }
        // Crisp glowing orbs: through the frosted panels they melt into soft
        // colour, which is what makes the glass read as glass.
        float[][] orbs = { // x, y, radius (fractions of width), colour index
                {0.30f, 0.20f, 0.085f, 1},
                {0.80f, 0.17f, 0.060f, 3},
                {0.88f, 0.72f, 0.120f, 2},
                {0.12f, 0.80f, 0.070f, 4},
        };
        for (float[] o : orbs) {
            float cx = o[0] * w, cy = o[1] * h, r = o[2] * w;
            int col = p[(int) o[3]];
            paint.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.4f,
                    new int[]{lighten(col), col, darken(col)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, paint);
        }
        // Darken the bottom a little so labels stay readable.
        paint.setShader(new LinearGradient(0, h * 0.4f, 0, h,
                0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        return b;
    }

    private static Bitmap decodePhoto(File f, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            if (src == null) return null;
            // Centre-crop to 16:9 and dim slightly for contrast.
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            float sw = w / scale, sh = h / scale;
            float sx = (src.getWidth() - sw) / 2f, sy = (src.getHeight() - sh) / 2f;
            Rect from = new Rect((int) sx, (int) sy, (int) (sx + sw), (int) (sy + sh));
            c.drawBitmap(src, from, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
            c.drawColor(0x40000000);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int lighten(int c) {
        return Color.rgb(Math.min(255, Color.red(c) + 90), Math.min(255, Color.green(c) + 90),
                Math.min(255, Color.blue(c) + 90));
    }

    private static int darken(int c) {
        return Color.rgb(Color.red(c) * 5 / 10, Color.green(c) * 5 / 10, Color.blue(c) * 5 / 10);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Draws the full sharp background to fill a view. */
    static void drawBackground(Canvas c, View v) {
        Bitmap b = sharp;
        if (b == null) {
            c.drawColor(Color.BLACK);
            return;
        }
        c.drawBitmap(b, null, new Rect(0, 0, v.getWidth(), v.getHeight()), BLUR_PAINT_NO_SHADER);
    }

    private static final Paint BLUR_PAINT_NO_SHADER = new Paint(Paint.FILTER_BITMAP_FLAG);

    /**
     * Draws a glass panel covering the view.
     *
     * @param focus 0 = resting, 1 = focused (brighter tint and border)
     */
    static void drawPanel(Canvas c, View v, float radius, float focus, LinearGradient highlight) {
        int w = v.getWidth(), h = v.getHeight();
        if (w == 0 || h == 0) return;
        float density = v.getResources().getDisplayMetrics().density;

        synchronized (Glass.class) {
            if (shader != null) {
                View root = v.getRootView();
                v.getLocationInWindow(LOC);
                MATRIX.setScale(root.getWidth() / (float) blurred.getWidth(),
                        root.getHeight() / (float) blurred.getHeight());
                MATRIX.postTranslate(-LOC[0], -LOC[1]);
                shader.setLocalMatrix(MATRIX);
                c.drawRoundRect(0, 0, w, h, radius, radius, BLUR_PAINT);
            }
        }

        // Frosted tint: white, a little stronger when focused.
        TINT_PAINT.setShader(null);
        TINT_PAINT.setColor(Color.argb((int) (28 + 34 * focus), 255, 255, 255));
        c.drawRoundRect(0, 0, w, h, radius, radius, TINT_PAINT);

        // Light catching the top edge of the glass.
        if (highlight != null) {
            TINT_PAINT.setShader(highlight);
            c.drawRoundRect(0, 0, w, h, radius, radius, TINT_PAINT);
            TINT_PAINT.setShader(null);
        }

        // Thin bright rim.
        float stroke = (1f + 1.5f * focus) * density;
        BORDER_PAINT.setStrokeWidth(stroke);
        BORDER_PAINT.setColor(Color.argb((int) (70 + 150 * focus), 255, 255, 255));
        float s = stroke / 2f;
        c.drawRoundRect(s, s, w - s, h - s, radius, radius, BORDER_PAINT);
    }

    static LinearGradient highlightFor(int h) {
        return new LinearGradient(0, 0, 0, h * 0.55f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
    }
}
