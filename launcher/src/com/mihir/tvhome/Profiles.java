package com.mihir.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * Who is using the TV: nobody (locked, calendar only), an adult, or the kids for a limited time.
 * The adults' PIN is stored only as a salted SHA-256 hash in this app's private preferences.
 * Wipe it with "adb shell pm clear com.mihir.tvhome" if it is ever forgotten.
 */
final class Profiles {

    enum Mode { LOCKED, ADULT, KIDS }

    static final int PIN_LENGTH = 6;
    /** An adult session ends after this long without coming back to the home screen. */
    static final long ADULT_IDLE_MS = 4 * 60 * 60 * 1000L;
    static final long KIDS_WARNING_MS = 5 * 60 * 1000L;
    private static final int MAX_TRIES = 5;

    private final Context ctx;
    private final SharedPreferences prefs;

    Profiles(Context c) {
        ctx = c.getApplicationContext();
        prefs = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- mode

    /** The current mode, locking first if the TV rebooted, the kids' time ran out or the adult went idle. */
    Mode mode() {
        Mode m = Mode.valueOf(prefs.getString("mode", Mode.LOCKED.name()));
        if (m == Mode.LOCKED) return m;
        long now = System.currentTimeMillis();
        boolean rebooted = prefs.getInt("boot", -1) != bootCount();
        boolean expired = m == Mode.KIDS && now >= prefs.getLong("kidsUntil", 0);
        boolean idle = m == Mode.ADULT && now - prefs.getLong("seen", 0) > ADULT_IDLE_MS;
        if (rebooted || expired || idle) { lock(); return Mode.LOCKED; }
        return m;
    }

    void unlockAdult() {
        prefs.edit().putString("mode", Mode.ADULT.name()).putInt("boot", bootCount())
                .putLong("seen", System.currentTimeMillis()).apply();
        cancelAlarms();
    }

    void unlockKids(int minutes) {
        long until = System.currentTimeMillis() + minutes * 60_000L;
        prefs.edit().putString("mode", Mode.KIDS.name()).putInt("boot", bootCount())
                .putLong("kidsUntil", until).apply();
        scheduleAlarms(until);
    }

    void lock() {
        prefs.edit().putString("mode", Mode.LOCKED.name()).apply();
        cancelAlarms();
    }

    /** Called on every interaction with the home screen, so an adult who keeps using it stays signed in. */
    void touch() {
        if (Mode.ADULT.name().equals(prefs.getString("mode", ""))) {
            prefs.edit().putLong("seen", System.currentTimeMillis()).apply();
        }
    }

    long kidsMinutesLeft() {
        long ms = prefs.getLong("kidsUntil", 0) - System.currentTimeMillis();
        return Math.max(0, (ms + 59_999) / 60_000);
    }

    private int bootCount() {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
    }

    // ---------------------------------------------------------------- kids timer

    private void scheduleAlarms(long until) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        if (until - KIDS_WARNING_MS > now) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until - KIDS_WARNING_MS, alarm(KidsTimer.ACTION_WARN));
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, alarm(KidsTimer.ACTION_END));
    }

    private void cancelAlarms() {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(alarm(KidsTimer.ACTION_WARN));
        am.cancel(alarm(KidsTimer.ACTION_END));
    }

    private PendingIntent alarm(String action) {
        Intent i = new Intent(ctx, KidsTimer.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, action.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---------------------------------------------------------------- PIN

    boolean hasPin() { return prefs.contains("pinHash"); }

    void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = Base64.encodeToString(salt, Base64.NO_WRAP);
        prefs.edit().putString("pinSalt", s).putString("pinHash", hash(s, pin))
                .putInt("fails", 0).putLong("blockedUntil", 0).apply();
    }

    /** Milliseconds the PIN pad stays blocked after too many wrong tries; 0 when it can be used. */
    long blockedMs() {
        return Math.max(0, prefs.getLong("blockedUntil", 0) - System.currentTimeMillis());
    }

    boolean checkPin(String pin) {
        if (blockedMs() > 0 || !hasPin()) return false;
        boolean ok = hash(prefs.getString("pinSalt", ""), pin).equals(prefs.getString("pinHash", ""));
        int fails = ok ? 0 : prefs.getInt("fails", 0) + 1;
        SharedPreferences.Editor e = prefs.edit().putInt("fails", fails);
        if (fails > 0 && fails % MAX_TRIES == 0) {
            // 1 minute after 5 wrong tries, doubling each further round, at most 15 minutes.
            long wait = Math.min(15 * 60_000L, 60_000L << Math.min(4, fails / MAX_TRIES - 1));
            e.putLong("blockedUntil", System.currentTimeMillis() + wait);
        }
        e.apply();
        return ok;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(md.digest(pin.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- calendars shown without a PIN

    /** Calendar ids shown on the locked screen; null means all of them (until the adults choose). */
    Set<String> visibleCalendars() {
        Set<String> s = prefs.getStringSet("calendars", null);
        return s == null ? null : new HashSet<>(s);
    }

    void setVisibleCalendars(Set<String> ids) {
        prefs.edit().putStringSet("calendars", new HashSet<>(ids)).apply();
    }
}
