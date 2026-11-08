package io.github.debloatgtv.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** A FrameLayout painted as frosted glass that glows and lifts when focused. */
class GlassFrame extends FrameLayout {

    private final float radius;
    private final float liftScale;
    private float focus;
    private boolean highlighted;
    private ValueAnimator anim;
    private LinearGradient highlight;

    GlassFrame(Context c, float radiusDp, float liftScale) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        this.radius = radiusDp * d;
        this.liftScale = liftScale;
        setWillNotDraw(false);
        setClipToPadding(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        highlight = Glass.highlightFor(h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Glass.drawPanel(canvas, this, radius, focus, highlight);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        setHighlighted(gained);
    }

    /** Used directly by GridView items, which are "selected" rather than focused. */
    void setHighlighted(boolean on) {
        if (on == highlighted) return;
        highlighted = on;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(focus, on ? 1f : 0f);
        anim.setDuration(140);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            focus = (float) a.getAnimatedValue();
            float s = 1f + (liftScale - 1f) * focus;
            setScaleX(s);
            setScaleY(s);
            setTranslationZ(focus * 8f);
            invalidate();
        });
        anim.start();
    }

    /** Re-draw this and every glass child (after a theme change or scroll). */
    static void invalidateTree(View v) {
        v.invalidate();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateTree(g.getChildAt(i));
        }
    }
}
