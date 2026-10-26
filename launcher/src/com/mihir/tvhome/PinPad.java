package com.mihir.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A centred PIN pad: six dots, an on-screen keypad for the D-pad, and the remote's number keys.
 * It only collects digits; the caller decides what a PIN means via {@link Listener}.
 */
final class PinPad {

    interface Listener {
        /** Called with all {@link Profiles#PIN_LENGTH} digits; call error() to retry, or dismiss(). */
        void onPin(PinPad pad, String pin);
    }

    private final Activity act;
    private final PopupWindow window;
    private final TextView title, message;
    private final List<View> dots = new ArrayList<>();
    private final StringBuilder digits = new StringBuilder();
    private final Listener listener;

    PinPad(Activity a, String heading, Listener l) {
        act = a;
        listener = l;

        LinearLayout box = new LinearLayout(a) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                int k = e.getKeyCode();
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) { add((char) ('0' + k - KeyEvent.KEYCODE_0)); return true; }
                    if (k == KeyEvent.KEYCODE_DEL) { back(); return true; }
                }
                return super.dispatchKeyEvent(e);
            }
        };
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(32), dp(26), dp(32), dp(26));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0xF51C1C1E);
        bg.setStroke(dp(1), 0x26FFFFFF);
        box.setBackground(bg);

        title = text(18, "sans-serif-light", Color.WHITE);
        title.setText(heading);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout dotRow = new LinearLayout(a);
        dotRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < Profiles.PIN_LENGTH; i++) {
            View d = new View(a);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(14), dp(14));
            p.setMargins(dp(7), 0, dp(7), 0);
            dotRow.addView(d, p);
            dots.add(d);
        }
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(-2, -2);
        dotsLp.setMargins(0, dp(18), 0, dp(8));
        box.addView(dotRow, dotsLp);

        message = text(12, "sans-serif", 0xFFFF8A80);
        message.setMinHeight(dp(20));
        message.setGravity(Gravity.CENTER);
        box.addView(message, new LinearLayout.LayoutParams(-1, -2));

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕"};
        TextView[] cells = new TextView[keys.length];
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER);
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                TextView t = key(keys[i]);
                cells[i] = t;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(64), dp(48));
                p.setMargins(dp(6), dp(6), dp(6), dp(6));
                row.addView(t, p);
            }
            box.addView(row);
        }
        // Explicit D-pad targets inside the 3x4 grid, no wrapping.
        for (int i = 0; i < cells.length; i++) {
            int r = i / 3, c = i % 3;
            TextView t = cells[i];
            t.setNextFocusLeftId(c == 0 ? t.getId() : cells[i - 1].getId());
            t.setNextFocusRightId(c == 2 ? t.getId() : cells[i + 1].getId());
            t.setNextFocusUpId(r == 0 ? t.getId() : cells[i - 3].getId());
            t.setNextFocusDownId(r == 3 ? t.getId() : cells[i + 3].getId());
        }
        cells[9].setOnClickListener(v -> back());
        cells[11].setOnClickListener(v -> dismiss());

        // Fixed width: wrap_content let the window come out narrower than the 3-column keypad.
        window = new PopupWindow(box, dp(380), -2, true);
        window.setElevation(dp(24));
        refresh();
        window.showAtLocation(a.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
        cells[4].requestFocus();
    }

    void setTitle(String t) { title.setText(t); }

    /** Clears the digits and shows why the PIN was not accepted. */
    void error(String why) {
        digits.setLength(0);
        message.setText(why);
        refresh();
    }

    void reset(String newTitle) {
        digits.setLength(0);
        message.setText("");
        title.setText(newTitle);
        refresh();
    }

    void dismiss() { window.dismiss(); }

    void setOnDismiss(Runnable r) { window.setOnDismissListener(r::run); }

    private void add(char c) {
        if (digits.length() >= Profiles.PIN_LENGTH) return;
        digits.append(c);
        message.setText("");
        refresh();
        if (digits.length() == Profiles.PIN_LENGTH) {
            String pin = digits.toString();
            // Let the last dot paint before the check runs.
            title.post(() -> listener.onPin(this, pin));
        }
    }

    private void back() {
        if (digits.length() > 0) digits.setLength(digits.length() - 1);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < dots.size(); i++) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            if (i < digits.length()) d.setColor(Color.WHITE);
            else d.setStroke(dp(2), 0x99FFFFFF);
            dots.get(i).setBackground(d);
        }
    }

    private TextView key(String label) {
        TextView t = text(20, "sans-serif-light", Color.WHITE);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setId(View.generateViewId());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x1FFFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x1FFFFFFF);
            t.setTextColor(has ? 0xFF111111 : Color.WHITE);
        });
        if (label.length() == 1 && Character.isDigit(label.charAt(0))) t.setOnClickListener(v -> add(label.charAt(0)));
        return t;
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(act);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }
}
