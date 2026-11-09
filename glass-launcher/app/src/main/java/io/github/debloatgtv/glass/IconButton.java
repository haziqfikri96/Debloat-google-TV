package io.github.debloatgtv.glass;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A round icon button; when focused it lights up and (optionally) shows its name. */
class IconButton extends LinearLayout {

    final Icon icon;
    private final TextView label;
    private final GradientDrawable bg = new GradientDrawable();

    IconButton(Context c, int kind, String name, float sizeDp) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setContentDescription(name);
        int pad = Math.round(sizeDp * 0.24f * d);
        setPadding(pad, pad, pad, pad);
        bg.setCornerRadius(999);
        bg.setColor(Color.TRANSPARENT);
        setBackground(bg);
        icon = new Icon(c, kind);
        int s = Math.round(sizeDp * 0.52f * d);
        addView(icon, new LayoutParams(s, s));
        if (name != null) {
            label = new TextView(c);
            label.setText(name);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setTextColor(0xFF1B1B1F);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setPadding(Math.round(7 * d), 0, Math.round(4 * d), 0);
            label.setVisibility(GONE);
            addView(label, new LayoutParams(-2, -2));
        } else {
            label = null;
        }
        setMinimumWidth(Math.round(sizeDp * d));
        setMinimumHeight(Math.round(sizeDp * d));
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        bg.setColor(gained ? 0xF2FFFFFF : Color.TRANSPARENT);
        icon.setDark(gained); // dark icon on the white focus pill, white on glass
        if (label != null) label.setVisibility(gained ? VISIBLE : GONE);
        animate().scaleX(gained ? 1.08f : 1f).scaleY(gained ? 1.08f : 1f).setDuration(140).start();
    }
}
