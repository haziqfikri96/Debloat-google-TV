package io.github.debloatgtv.glass;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * StandBy clock screensaver: a big clock on black with the date, Hijri date and
 * the next prayer. Works offline (prayer times come from the cached timetable).
 * The clock drifts a little every minute so it never burns into the panel, and
 * turns dim red late at night.
 */
public class ClockDream extends DreamService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private LinearLayout box;
    private TextView time, date, next;
    private Prayer prayer;

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(float sp, boolean light) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(light ? "sans-serif-thin" : "sans-serif-light", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        prayer = new Prayer(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        time = text(110, true);
        date = text(20, false);
        date.setPadding(0, dp(10), 0, dp(22));
        next = text(20, false);
        next.setPadding(dp(22), dp(10), dp(22), dp(10));
        box.addView(time);
        box.addView(date);
        box.addView(next, new LinearLayout.LayoutParams(-2, -2));
        root.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        tick.run();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            // Next update on the minute.
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - now % 60_000 + 50);
        }
    };

    private void update() {
        Calendar now = Calendar.getInstance();
        Date d = now.getTime();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        boolean night = hour >= 23 || hour < 5;
        int main = night ? 0xFF8A1C1C : 0xFFF2F2F2;
        int soft = night ? 0xFF5E1414 : 0xB3FFFFFF;

        time.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", d));
        time.setTextColor(main);
        Prayer.Day day = null;
        try {
            day = prayer.day(now);
        } catch (RuntimeException ignored) {
            // No location yet: clock and date only.
        }
        String dateText = DateFormat.format("EEEE, d MMMM", d).toString();
        if (day != null && day.hijri != null) dateText += "  ·  " + day.hijri;
        date.setText(dateText);
        date.setTextColor(soft);

        if (day != null) {
            String[] names = day.malay
                    ? new String[]{"Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"}
                    : new String[]{"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
            int nowMin = hour * 60 + now.get(Calendar.MINUTE);
            int i = 0;
            while (i < 6 && day.minutes[i] <= nowMin) i++;
            String label;
            int left;
            if (i < 6) {
                label = names[i];
                left = day.minutes[i] - nowMin;
            } else {
                label = names[0];
                left = 24 * 60 - nowMin + day.minutes[0];
            }
            String in = (left >= 60 ? left / 60 + (day.malay ? "j " : "h ") : "") + left % 60 + "m";
            next.setText(label + "  ·  " + (day.malay ? "dalam " : "in ") + in);
            next.setTextColor(main);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(24));
            pill.setStroke(Math.max(1, dp(1.5f)), night ? 0x665E1414 : 0x55FFFFFF);
            pill.setColor(night ? 0x00000000 : 0x14FFFFFF);
            next.setBackground(pill);
            next.setVisibility(android.view.View.VISIBLE);
        } else {
            next.setVisibility(android.view.View.GONE);
        }

        // Burn-in protection: drift around the centre.
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        box.setTranslationX((random.nextFloat() - 0.5f) * w * 0.25f);
        box.setTranslationY((random.nextFloat() - 0.5f) * h * 0.25f);
    }
}
