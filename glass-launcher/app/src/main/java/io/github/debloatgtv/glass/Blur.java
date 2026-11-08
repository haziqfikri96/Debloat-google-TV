package io.github.debloatgtv.glass;

import android.graphics.Bitmap;

/** Fast in-place box blur; a few passes approximate a Gaussian. */
final class Blur {
    private Blur() {}

    static void box(Bitmap bmp, int radius, int passes) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] a = new int[w * h], b = new int[w * h];
        bmp.getPixels(a, 0, w, 0, 0, w, h);
        for (int p = 0; p < passes; p++) {
            pass(a, b, w, h, radius, true);
            pass(b, a, w, h, radius, false);
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int win = r * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[idx(line, clamp(i, len), w, horizontal)];
                sa += c >>> 24; sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                dst[idx(line, i, w, horizontal)] =
                        ((sa / win) << 24) | ((sr / win) << 16) | ((sg / win) << 8) | (sb / win);
                int out = src[idx(line, clamp(i - r, len), w, horizontal)];
                int in = src[idx(line, clamp(i + r + 1, len), w, horizontal)];
                sa += (in >>> 24) - (out >>> 24);
                sr += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                sg += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                sb += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static int clamp(int i, int len) { return i < 0 ? 0 : (i >= len ? len - 1 : i); }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
