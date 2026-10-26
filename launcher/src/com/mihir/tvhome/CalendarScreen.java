package com.mihir.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * The family calendar shown while nobody is signed in: week (default), day or month view of the
 * Google account's calendars synced on the TV, read-only, plus the Kids / Adult buttons.
 * Reads the local calendar provider only; no network.
 */
final class CalendarScreen {

    interface Actions {
        void onKids();
        void onAdult();
        /** Start speech input; the result comes back through {@link #handleSpeech}. */
        void onVoice();
        /** Close the calendar when it was opened from a profile's home screen. */
        void onBack();
    }

    enum Range { DAY, WEEK, MONTH }

    static final class Event {
        long begin, end;
        boolean allDay;
        String title;
        int color;
    }

    private final Activity act;
    private final Profiles profiles;
    private final Actions actions;
    private final FrameLayout root;
    private final FrameLayout body;
    private final TextView heading, clock;
    private final List<TextView> buttons = new ArrayList<>();
    private TextView kidsButton, adultButton, newButton, backButton;
    private boolean viewer;
    private Range range = Range.WEEK;
    private final Calendar anchor = Calendar.getInstance();
    private int loadSeq;

    CalendarScreen(Activity a, Profiles p, Actions acts) {
        act = a;
        profiles = p;
        actions = acts;

        root = new FrameLayout(a);
        root.setBackgroundColor(0x99000000);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(30), dp(48), dp(30));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // Top bar: what is shown on the left, navigation and profile buttons on the right.
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        // Small line above the heading: the time, then the screen's name.
        LinearLayout labelRow = new LinearLayout(a);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        clock = text(12, "sans-serif-medium", Color.WHITE);
        labelRow.addView(clock);
        TextView label = text(10, "sans-serif-medium", 0xB3FFFFFF);
        label.setText("   ·   CALENDÁRIO DA FAMÍLIA");
        label.setLetterSpacing(0.25f);
        label.setSingleLine(true);
        labelRow.addView(label);
        left.addView(labelRow);
        heading = text(26, "sans-serif-light", Color.WHITE);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(heading);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        addButton(top, "◀", v -> step(-1));
        addButton(top, "Hoje", v -> { anchor.setTimeInMillis(System.currentTimeMillis()); render(); });
        addButton(top, "▶", v -> step(1));
        addGap(top);
        addButton(top, "Dia", v -> { range = Range.DAY; render(); });
        addButton(top, "Semana", v -> { range = Range.WEEK; render(); });
        addButton(top, "Mês", v -> { range = Range.MONTH; render(); });
        addGap(top);
        newButton = addButton(top, "+ Compromisso", v -> actions.onVoice());
        addGap(top);
        kidsButton = addButton(top, "Crianças", v -> actions.onKids());
        adultButton = addButton(top, "Adulto", v -> actions.onAdult());
        backButton = addButton(top, "Voltar", v -> actions.onBack());
        configure(false, true);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        body = new FrameLayout(a);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bp.setMargins(0, dp(22), 0, 0);
        col.addView(body, bp);

    }

    View view() { return root; }

    /**
     * viewer: opened from a profile (shows Voltar instead of Crianças/Adulto);
     * canCreate: shows "+ Compromisso" for adding an event by voice.
     */
    void configure(boolean asViewer, boolean canCreate) {
        viewer = asViewer;
        kidsButton.setVisibility(asViewer ? View.GONE : View.VISIBLE);
        adultButton.setVisibility(asViewer ? View.GONE : View.VISIBLE);
        backButton.setVisibility(asViewer ? View.VISIBLE : View.GONE);
        newButton.setVisibility(canCreate ? View.VISIBLE : View.GONE);
        // Explicit D-pad targets across the visible buttons only; no wrapping, nothing above or below.
        List<TextView> shown = new ArrayList<>();
        for (TextView b : buttons) if (b.getVisibility() == View.VISIBLE) shown.add(b);
        for (int i = 0; i < shown.size(); i++) {
            TextView b = shown.get(i);
            b.setNextFocusLeftId(i == 0 ? b.getId() : shown.get(i - 1).getId());
            b.setNextFocusRightId(i == shown.size() - 1 ? b.getId() : shown.get(i + 1).getId());
            b.setNextFocusUpId(b.getId());
            b.setNextFocusDownId(b.getId());
        }
    }

    /** Channel up/down on the remote page through the calendar; called by the activity. */
    boolean onKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_PAGE_UP) { step(-1); return true; }
        if (k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_PAGE_DOWN) { step(1); return true; }
        return false;
    }

    void focus() { (viewer ? backButton : kidsButton).requestFocus(); }

    void updateClock() {
        boolean is24 = android.text.format.DateFormat.is24HourFormat(act);
        clock.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm a", Locale.getDefault()).format(new Date()));
    }

    /** Back to this week, as on a fresh start. */
    void resetToToday() {
        range = Range.WEEK;
        anchor.setTimeInMillis(System.currentTimeMillis());
        render();
    }

    void render() {
        updateClock();
        long[] span = span();
        heading.setText(title(span));
        int seq = ++loadSeq;
        new Thread(() -> {
            List<Event> events = load(span[0], span[1]);
            act.runOnUiThread(() -> { if (seq == loadSeq && !act.isDestroyed()) show(span, events); });
        }).start();
    }

    private void step(int dir) {
        anchor.add(range == Range.DAY ? Calendar.DAY_OF_YEAR : range == Range.WEEK ? Calendar.WEEK_OF_YEAR : Calendar.MONTH, dir);
        render();
    }

    // ---------------------------------------------------------------- range

    /** [start, end) of what is on screen; the month view covers whole weeks, Monday first. */
    private long[] span() {
        Calendar s = startOfDay(anchor.getTimeInMillis());
        Calendar e;
        if (range == Range.DAY) {
            e = (Calendar) s.clone();
            e.add(Calendar.DAY_OF_YEAR, 1);
        } else if (range == Range.WEEK) {
            toMonday(s);
            e = (Calendar) s.clone();
            e.add(Calendar.DAY_OF_YEAR, 7);
        } else {
            s.set(Calendar.DAY_OF_MONTH, 1);
            toMonday(s);
            e = (Calendar) s.clone();
            e.add(Calendar.DAY_OF_YEAR, 42);
        }
        return new long[]{s.getTimeInMillis(), e.getTimeInMillis()};
    }

    private String title(long[] span) {
        Locale l = Locale.getDefault();
        if (range == Range.DAY) return new SimpleDateFormat("EEEE, d 'de' MMMM", l).format(new Date(span[0]));
        if (range == Range.MONTH) return new SimpleDateFormat("MMMM 'de' yyyy", l).format(anchor.getTime());
        Date a = new Date(span[0]), b = new Date(span[1] - 1);
        return new SimpleDateFormat("d MMM", l).format(a) + " – " + new SimpleDateFormat("d MMM yyyy", l).format(b);
    }

    private static Calendar startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static void toMonday(Calendar c) {
        int back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        c.add(Calendar.DAY_OF_YEAR, -back);
    }

    // ---------------------------------------------------------------- data

    private boolean canRead() {
        return act.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private List<Event> load(long from, long to) {
        List<Event> out = new ArrayList<>();
        if (!canRead()) return out;
        Set<String> visible = profiles.visibleCalendars();
        String[] cols = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.DISPLAY_COLOR};
        // All-day events are stored in UTC; widen the window so they are not cut at the edges.
        try (Cursor c = CalendarContract.Instances.query(act.getContentResolver(), cols,
                from - 86_400_000L, to + 86_400_000L)) {
            if (c == null) return out;
            TimeZone tz = TimeZone.getDefault();
            while (c.moveToNext()) {
                if (visible != null && !visible.contains(String.valueOf(c.getLong(4)))) continue;
                Event e = new Event();
                e.allDay = c.getInt(2) != 0;
                e.begin = c.getLong(0);
                e.end = c.getLong(1);
                if (e.allDay) { e.begin -= tz.getOffset(e.begin); e.end -= tz.getOffset(e.end); }
                if (e.end <= from || e.begin >= to) continue;
                e.title = c.isNull(3) || TextUtils.isEmpty(c.getString(3)) ? "(sem título)" : c.getString(3);
                e.color = c.isNull(5) ? 0xFF4285F4 : (c.getInt(5) | 0xFF000000);
                out.add(e);
            }
        } catch (RuntimeException ignored) { }
        Collections.sort(out, (x, y) -> x.allDay != y.allDay ? (x.allDay ? -1 : 1) : Long.compare(x.begin, y.begin));
        return out;
    }

    /** Events touching [dayStart, dayStart + 1 day). */
    private static List<Event> on(List<Event> all, long dayStart) {
        Calendar n = Calendar.getInstance();
        n.setTimeInMillis(dayStart);
        n.add(Calendar.DAY_OF_YEAR, 1);
        long dayEnd = n.getTimeInMillis();
        List<Event> out = new ArrayList<>();
        for (Event e : all) if (e.begin < dayEnd && e.end > dayStart) out.add(e);
        return out;
    }

    // ---------------------------------------------------------------- views

    private void show(long[] span, List<Event> events) {
        body.removeAllViews();
        if (!canRead()) { body.addView(note("Sem permissão para ler a agenda.")); return; }
        if (range == Range.DAY) body.addView(dayView(span[0], events), new FrameLayout.LayoutParams(-1, -1));
        else if (range == Range.WEEK) body.addView(weekView(span[0], events), new FrameLayout.LayoutParams(-1, -1));
        else body.addView(monthView(span[0], events), new FrameLayout.LayoutParams(-1, -1));
    }

    private View weekView(long start, List<Event> events) {
        LinearLayout row = new LinearLayout(act);
        Calendar d = startOfDay(start);
        long today = startOfDay(System.currentTimeMillis()).getTimeInMillis();
        for (int i = 0; i < 7; i++) {
            long day = d.getTimeInMillis();
            LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(10), dp(10), dp(10), dp(10));
            col.setBackground(panel(day == today ? 0x33FFFFFF : 0x1AFFFFFF));
            col.addView(dayHeader(d, day == today));
            List<Event> list = on(events, day);
            int max = 8;
            for (int j = 0; j < Math.min(max, list.size()); j++) col.addView(chip(list.get(j), true));
            if (list.size() > max) col.addView(more(list.size() - max));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
            p.setMargins(i == 0 ? 0 : dp(5), 0, i == 6 ? 0 : dp(5), 0);
            row.addView(col, p);
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        return row;
    }

    private View dayView(long start, List<Event> events) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(28), dp(22), dp(28), dp(22));
        col.setBackground(panel(0x1AFFFFFF));
        List<Event> list = on(events, start);
        if (list.isEmpty()) {
            col.addView(note("Nenhum compromisso neste dia."));
            return col;
        }
        SimpleDateFormat hm = new SimpleDateFormat("H:mm", Locale.getDefault());
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Event e = list.get(i);
            LinearLayout line = new LinearLayout(act);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(8), 0, dp(8));
            View bar = new View(act);
            GradientDrawable b = new GradientDrawable();
            b.setCornerRadius(dp(3));
            b.setColor(e.color);
            bar.setBackground(b);
            line.addView(bar, new LinearLayout.LayoutParams(dp(6), dp(30)));
            TextView time = text(16, "sans-serif-medium", 0xCCFFFFFF);
            time.setText(e.allDay ? "Dia todo" : hm.format(new Date(e.begin)) + " – " + hm.format(new Date(e.end)));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(170), -2);
            tp.setMargins(dp(16), 0, 0, 0);
            line.addView(time, tp);
            TextView t = text(20, "sans-serif", Color.WHITE);
            t.setText(e.title);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(line);
        }
        if (list.size() > 10) col.addView(more(list.size() - 10));
        return col;
    }

    private View monthView(long start, List<Event> events) {
        LinearLayout grid = new LinearLayout(act);
        grid.setOrientation(LinearLayout.VERTICAL);
        int month = anchor.get(Calendar.MONTH);
        long today = startOfDay(System.currentTimeMillis()).getTimeInMillis();
        Calendar d = startOfDay(start);
        LinearLayout names = new LinearLayout(act);
        SimpleDateFormat wd = new SimpleDateFormat("EEE", Locale.getDefault());
        Calendar n = (Calendar) d.clone();
        for (int i = 0; i < 7; i++) {
            TextView t = text(11, "sans-serif-medium", 0x99FFFFFF);
            t.setText(wd.format(n.getTime()).replace(".", "").toUpperCase(Locale.getDefault()));
            t.setGravity(Gravity.CENTER);
            names.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            n.add(Calendar.DAY_OF_YEAR, 1);
        }
        grid.addView(names, new LinearLayout.LayoutParams(-1, -2));
        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(act);
            for (int c = 0; c < 7; c++) {
                long day = d.getTimeInMillis();
                boolean inMonth = d.get(Calendar.MONTH) == month;
                LinearLayout cell = new LinearLayout(act);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(dp(6), dp(2), dp(6), dp(2));
                cell.setBackground(panel(day == today ? 0x40FFFFFF : inMonth ? 0x1AFFFFFF : 0x0DFFFFFF));
                TextView num = text(12, day == today ? "sans-serif-medium" : "sans-serif", inMonth ? Color.WHITE : 0x66FFFFFF);
                num.setIncludeFontPadding(false);
                num.setText(String.valueOf(d.get(Calendar.DAY_OF_MONTH)));
                cell.addView(num);
                // A month cell fits two lines under the day number: two events, or one and "+N mais".
                List<Event> list = on(events, day);
                int fit = list.size() <= 2 ? list.size() : 1;
                for (int j = 0; j < fit; j++) cell.addView(chip(list.get(j), false));
                if (list.size() > fit) cell.addView(more(list.size() - fit));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
                p.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(cell, p);
                d.add(Calendar.DAY_OF_YEAR, 1);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        return grid;
    }

    private View dayHeader(Calendar d, boolean today) {
        LinearLayout h = new LinearLayout(act);
        h.setOrientation(LinearLayout.VERTICAL);
        TextView wd = text(11, "sans-serif-medium", today ? Color.WHITE : 0x99FFFFFF);
        wd.setText(new SimpleDateFormat("EEE", Locale.getDefault()).format(d.getTime()).replace(".", "").toUpperCase(Locale.getDefault()));
        wd.setLetterSpacing(0.15f);
        h.addView(wd);
        TextView num = text(28, today ? "sans-serif" : "sans-serif-light", Color.WHITE);
        num.setText(String.valueOf(d.get(Calendar.DAY_OF_MONTH)));
        h.addView(num);
        h.setPadding(0, 0, 0, dp(8));
        return h;
    }

    /** One event: a coloured rounded label, with the start time in the week view. */
    private View chip(Event e, boolean withTime) {
        TextView t = text(withTime ? 12 : 10, "sans-serif", Color.WHITE);
        String time = e.allDay || !withTime ? "" : new SimpleDateFormat("H:mm", Locale.getDefault()).format(new Date(e.begin)) + "  ";
        t.setText(time + e.title);
        t.setMaxLines(withTime ? 2 : 1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(6), dp(withTime ? 3 : 1), dp(6), dp(withTime ? 3 : 1));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(withAlpha(e.color, e.allDay ? 0.85f : 0.45f));
        t.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(withTime ? 5 : 2), 0, 0);
        t.setLayoutParams(p);
        return t;
    }

    private View more(int n) {
        TextView t = text(range == Range.MONTH ? 10 : 11, "sans-serif-medium", 0x99FFFFFF);
        t.setText("+" + n + " mais");
        t.setPadding(dp(4), dp(range == Range.MONTH ? 1 : 4), 0, 0);
        return t;
    }

    private View note(String s) {
        TextView t = text(18, "sans-serif-light", 0xCCFFFFFF);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        return t;
    }

    private TextView addButton(LinearLayout parent, String label, View.OnClickListener click) {
        TextView b = text(12, "sans-serif-medium", 0xE6FFFFFF);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(11), 0, dp(11), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setId(View.generateViewId());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x66000000);
        bg.setStroke(dp(1), 0x33FFFFFF);
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x66000000);
            b.setTextColor(has ? 0xFF111111 : 0xE6FFFFFF);
        });
        b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(32));
        p.setMargins(dp(6), 0, 0, 0);
        parent.addView(b, p);
        buttons.add(b);
        return b;
    }

    private void addGap(LinearLayout parent) {
        parent.addView(new View(act), new LinearLayout.LayoutParams(dp(10), 1));
    }

    private GradientDrawable panel(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(color);
        return g;
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

    private static int withAlpha(int c, float a) {
        return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c));
    }

    // ---------------------------------------------------------------- new event by voice

    /** Shows what was understood from the spoken sentence and saves it on "Salvar". */
    void handleSpeech(String spoken) {
        if (spoken == null || spoken.trim().isEmpty()) return;
        EventParser.Result r = EventParser.parse(spoken, Calendar.getInstance());
        long[] cal = writableCalendar();
        Locale l = Locale.getDefault();
        String when = new SimpleDateFormat("EEEE, d 'de' MMMM", l).format(r.day.getTime());
        String time = r.allDay() ? "Dia inteiro" : String.format(l, "%d:%02d – %d:%02d", r.hour, r.minute, (r.hour + 1) % 24, r.minute);
        StringBuilder msg = new StringBuilder();
        msg.append("Você disse: “").append(spoken.trim()).append("”\n\n")
                .append(r.title).append("\n").append(when).append(r.dateGuessed ? " (nenhum dia dito: hoje)" : "")
                .append("\n").append(time);
        if (cal == null) msg.append("\n\nNão há agenda gravável nesta TV.");
        AlertDialog.Builder b = new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Novo compromisso")
                .setMessage(msg.toString())
                .setNeutralButton("Falar de novo", (d, w) -> actions.onVoice())
                .setNegativeButton("Cancelar", null);
        if (cal != null) b.setPositiveButton("Salvar", (d, w) -> save(r, cal[0]));
        b.show();
    }

    /** The account's primary calendar the TV may write to, as {id}; null if there is none. */
    private long[] writableCalendar() {
        if (act.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null;
        String[] cols = {CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY};
        String where = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= " + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                + " AND " + CalendarContract.Calendars.ACCOUNT_TYPE + " = 'com.google'";
        long fallback = -1;
        try (Cursor c = act.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols, where, null, null)) {
            while (c != null && c.moveToNext()) {
                if (c.getInt(1) == 1) return new long[]{c.getLong(0)};
                if (fallback < 0) fallback = c.getLong(0);
            }
        } catch (RuntimeException ignored) { }
        return fallback < 0 ? null : new long[]{fallback};
    }

    private void save(EventParser.Result r, long calendarId) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        v.put(CalendarContract.Events.TITLE, r.title);
        if (r.allDay()) {
            // All-day events are stored as UTC midnights.
            Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            u.clear();
            u.set(r.day.get(Calendar.YEAR), r.day.get(Calendar.MONTH), r.day.get(Calendar.DAY_OF_MONTH));
            v.put(CalendarContract.Events.DTSTART, u.getTimeInMillis());
            u.add(Calendar.DAY_OF_YEAR, 1);
            v.put(CalendarContract.Events.DTEND, u.getTimeInMillis());
            v.put(CalendarContract.Events.ALL_DAY, 1);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
        } else {
            Calendar s = (Calendar) r.day.clone();
            s.set(Calendar.HOUR_OF_DAY, r.hour);
            s.set(Calendar.MINUTE, r.minute);
            v.put(CalendarContract.Events.DTSTART, s.getTimeInMillis());
            v.put(CalendarContract.Events.DTEND, s.getTimeInMillis() + 60 * 60_000L);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        }
        boolean ok;
        try {
            ok = act.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v) != null;
        } catch (RuntimeException e) {
            ok = false;
        }
        android.widget.Toast.makeText(act, ok ? "Compromisso salvo na agenda da família" : "Não foi possível salvar o compromisso",
                android.widget.Toast.LENGTH_LONG).show();
        if (ok) { anchor.setTimeInMillis(r.day.getTimeInMillis()); render(); }
    }

    // ---------------------------------------------------------------- calendar choice (adults only)

    /** Lets an adult tick which of the account's calendars appear on the locked screen. */
    static void chooseCalendars(Activity a, Profiles p) {
        if (a.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        String[] cols = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME};
        try (Cursor c = a.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols, null, null,
                CalendarContract.Calendars.ACCOUNT_NAME + ", " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)) {
            while (c != null && c.moveToNext()) {
                ids.add(String.valueOf(c.getLong(0)));
                String name = c.getString(1), account = c.getString(2);
                names.add(name != null && name.equals(account) ? name : name + "  ·  " + account);
            }
        } catch (RuntimeException ignored) { }
        if (ids.isEmpty()) return;
        Set<String> shown = p.visibleCalendars();
        boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) checked[i] = shown == null || shown.contains(ids.get(i));
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Agendas visíveis sem PIN")
                .setMultiChoiceItems(names.toArray(new String[0]), checked, (d, i, on) -> checked[i] = on)
                .setPositiveButton("Salvar", (d, w) -> {
                    Set<String> keep = new HashSet<>();
                    for (int i = 0; i < ids.size(); i++) if (checked[i]) keep.add(ids.get(i));
                    p.setVisibleCalendars(keep);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
