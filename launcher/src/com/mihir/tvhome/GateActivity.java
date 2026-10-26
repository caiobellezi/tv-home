package com.mihir.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Full-screen PIN prompt put over an app the current profile doesn't allow (started by Guard).
 * The right PIN lets that app through until the home screen comes back; Back or ✕ goes home.
 */
public class GateActivity extends Activity {

    static final String EXTRA_PACKAGE = "pkg";

    private Profiles profiles;
    private String pkg;
    private PinPad pad;
    private boolean passed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        profiles = new Profiles(this);
        pkg = getIntent().getStringExtra(EXTRA_PACKAGE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xF0000000);
        TextView note = new TextView(this);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        note.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        note.setTextColor(0xCCFFFFFF);
        note.setGravity(Gravity.CENTER);
        note.setText(label() + " precisa do PIN de um adulto");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.topMargin = Math.round(48 * getResources().getDisplayMetrics().density);
        root.addView(note, lp);
        setContentView(root);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pkg = intent.getStringExtra(EXTRA_PACKAGE);
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        // The PIN pad is a popup and needs the window to be attached first.
        if (has && pad == null) showPad();
    }

    private void showPad() {
        if (!profiles.hasPin()) { goHome(); return; }
        pad = new PinPad(this, "PIN para abrir " + label(), (p, pin) -> {
            long blocked = profiles.blockedMs();
            if (blocked > 0) { p.error("Muitas tentativas. Aguarde " + ((blocked + 999) / 1000) + " s."); return; }
            if (profiles.checkPin(pin)) {
                passed = true;
                profiles.grant(pkg);
                p.dismiss();
                return;
            }
            long now = profiles.blockedMs();
            p.error(now > 0 ? "Muitas tentativas. Aguarde " + ((now + 999) / 1000) + " s." : "PIN incorreto.");
        });
        pad.setOnDismiss(() -> {
            if (passed) finish(); // back to the app underneath, now allowed
            else goHome();
        });
    }

    @Override
    public void onBackPressed() { goHome(); }

    private void goHome() {
        if (isFinishing()) return;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(home); } catch (RuntimeException ignored) { }
        finish();
    }

    private String label() {
        if (pkg == null) return "Este app";
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return "Este app";
        }
    }
}
