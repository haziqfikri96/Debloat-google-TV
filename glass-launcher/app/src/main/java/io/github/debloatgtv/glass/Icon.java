package io.github.debloatgtv.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Simple line icons drawn on a 24×24 grid (no image files, no libraries). */
class Icon extends View {

    static final int ROCKET = 0, MUSIC = 1, WIFI = 2, PALETTE = 3, GEAR = 4, POWER = 5,
            PREV = 6, PLAY = 7, PAUSE = 8, NEXT = 9, HOME = 10;

    private int kind;
    private boolean dim;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    Icon(Context c, int kind) {
        super(c);
        this.kind = kind;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xF2FFFFFF);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xF2FFFFFF);
        stroke.setShadowLayer(3f, 0, 1f, 0x55000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // shadow layer on paths
    }

    void setDark(boolean dark) {
        int col = dark ? 0xFF1B1B1F : 0xF2FFFFFF;
        stroke.setColor(col);
        fill.setColor(col);
        dim = dark;
        invalidate();
    }

    void setKind(int k) {
        if (k == kind) return;
        kind = k;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        c.scale(s, s);
        stroke.setStrokeWidth(1.9f);
        if (dim) stroke.clearShadowLayer(); else stroke.setShadowLayer(3f / s, 0, 1f / s, 0x55000000);
        path.reset();
        switch (kind) {
            case ROCKET: rocket(c); break;
            case MUSIC: music(c); break;
            case WIFI: wifi(c); break;
            case PALETTE: palette(c); break;
            case GEAR: gear(c); break;
            case POWER: power(c); break;
            case PREV: prev(c); break;
            case PLAY: play(c); break;
            case PAUSE: pause(c); break;
            case NEXT: next(c); break;
            case HOME: home(c); break;
        }
        c.restore();
    }

    private void rocket(Canvas c) {
        path.moveTo(12, 2.5f);
        path.cubicTo(15.5f, 5, 16.5f, 9, 15.8f, 15);
        path.lineTo(8.2f, 15);
        path.cubicTo(7.5f, 9, 8.5f, 5, 12, 2.5f);
        path.close();
        // fins
        path.moveTo(8.4f, 11.5f); path.lineTo(5.2f, 15.5f); path.lineTo(8.2f, 15);
        path.moveTo(15.6f, 11.5f); path.lineTo(18.8f, 15.5f); path.lineTo(15.8f, 15);
        c.drawPath(path, stroke);
        c.drawCircle(12, 9, 1.7f, stroke);
        // flame
        path.reset();
        path.moveTo(10.3f, 17.3f); path.lineTo(12, 21.3f); path.lineTo(13.7f, 17.3f);
        c.drawPath(path, stroke);
    }

    private void music(Canvas c) {
        c.drawCircle(8.2f, 17.5f, 2.6f, fill);
        path.moveTo(10.6f, 17.5f);
        path.lineTo(10.6f, 4);
        path.cubicTo(13, 5.5f, 17, 6, 17.5f, 9.5f);
        path.cubicTo(16, 8.3f, 13, 8, 10.6f, 7.8f);
        c.drawPath(path, stroke);
    }

    private void wifi(Canvas c) {
        float cx = 12, cy = 19;
        for (float r : new float[]{4.6f, 9.2f, 13.8f}) {
            oval.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(oval, 225, 90, false, stroke);
        }
        c.drawCircle(cx, cy, 1.5f, fill);
    }

    private void palette(Canvas c) {
        path.moveTo(12, 3);
        path.cubicTo(6.5f, 3, 3, 7, 3, 12);
        path.cubicTo(3, 17, 7, 21, 12, 21);
        path.cubicTo(13.5f, 21, 14, 20, 13.3f, 18.8f);
        path.cubicTo(12.5f, 17.4f, 13.4f, 15.8f, 15, 15.8f);
        path.lineTo(17, 15.8f);
        path.cubicTo(19.5f, 15.8f, 21, 14, 21, 11.5f);
        path.cubicTo(21, 6.7f, 17, 3, 12, 3);
        path.close();
        c.drawPath(path, stroke);
        c.drawCircle(7.6f, 11.5f, 1.3f, fill);
        c.drawCircle(10, 7.4f, 1.3f, fill);
        c.drawCircle(14.4f, 7.4f, 1.3f, fill);
        c.drawCircle(17, 11, 1.3f, fill);
    }

    private void gear(Canvas c) {
        float cx = 12, cy = 12;
        int teeth = 8;
        for (int i = 0; i < teeth * 2; i++) {
            double a0 = Math.PI * 2 * i / (teeth * 2) - Math.PI / 16;
            double a1 = Math.PI * 2 * (i + 1) / (teeth * 2) - Math.PI / 16;
            float r = i % 2 == 0 ? 9.2f : 7.0f;
            float x0 = cx + (float) (Math.cos(a0) * r), y0 = cy + (float) (Math.sin(a0) * r);
            float x1 = cx + (float) (Math.cos(a1) * r), y1 = cy + (float) (Math.sin(a1) * r);
            if (i == 0) path.moveTo(x0, y0); else path.lineTo(x0, y0);
            path.lineTo(x1, y1);
        }
        path.close();
        c.drawPath(path, stroke);
        c.drawCircle(cx, cy, 2.8f, stroke);
    }

    private void power(Canvas c) {
        oval.set(4, 4.5f, 20, 20.5f);
        c.drawArc(oval, -50, 280, false, stroke);
        c.drawLine(12, 2.5f, 12, 11.5f, stroke);
    }

    private void prev(Canvas c) {
        path.moveTo(18, 5.5f); path.lineTo(8.5f, 12); path.lineTo(18, 18.5f); path.close();
        c.drawPath(path, fill);
        c.drawRoundRect(5.2f, 5.5f, 7.6f, 18.5f, 1, 1, fill);
    }

    private void next(Canvas c) {
        path.moveTo(6, 5.5f); path.lineTo(15.5f, 12); path.lineTo(6, 18.5f); path.close();
        c.drawPath(path, fill);
        c.drawRoundRect(16.4f, 5.5f, 18.8f, 18.5f, 1, 1, fill);
    }

    private void play(Canvas c) {
        path.moveTo(8, 4.8f); path.lineTo(19.5f, 12); path.lineTo(8, 19.2f); path.close();
        c.drawPath(path, fill);
    }

    private void pause(Canvas c) {
        c.drawRoundRect(6.5f, 5, 10.2f, 19, 1.2f, 1.2f, fill);
        c.drawRoundRect(13.8f, 5, 17.5f, 19, 1.2f, 1.2f, fill);
    }

    private void home(Canvas c) {
        path.moveTo(3.5f, 11); path.lineTo(12, 3.8f); path.lineTo(20.5f, 11);
        path.moveTo(5.8f, 9.2f); path.lineTo(5.8f, 20); path.lineTo(18.2f, 20); path.lineTo(18.2f, 9.2f);
        path.moveTo(10, 20); path.lineTo(10, 14.5f); path.lineTo(14, 14.5f); path.lineTo(14, 20);
        c.drawPath(path, stroke);
    }
}
