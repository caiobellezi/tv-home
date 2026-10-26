package com.mihir.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Watches which app comes to the front and asks for the adults' PIN when the current profile
 * doesn't allow it: outside the Adult profile any app off the kids' list (however it was opened:
 * Mi key, voice search, Play Store, Inputs), and the always-PIN apps (YouTube) in every profile.
 * Enabled over ADB as an accessibility service; it only reads the package name of new windows.
 */
public class Guard extends AccessibilityService {

    private final Set<String> kidsApps = new HashSet<>();
    private final Set<String> alwaysPin = new HashSet<>();
    private final Set<String> protectedApps = new HashSet<>();
    private final Map<String, Boolean> hasIcon = new HashMap<>();
    private Profiles profiles;
    private String gating;

    @Override
    protected void onServiceConnected() {
        profiles = new Profiles(this);
        kidsApps.addAll(Arrays.asList(getResources().getStringArray(R.array.kids_apps)));
        kidsApps.add(getString(R.string.live_tv_package)); // TV ao vivo and the HDMI/AV inputs
        alwaysPin.addAll(Arrays.asList(getResources().getStringArray(R.array.always_pin_apps)));
        protectedApps.addAll(Arrays.asList(getResources().getStringArray(R.array.protected_apps)));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName())) { gating = null; return; }
        if (pkg.equals(gating) || profiles.isGranted(pkg) || !needsPin(pkg)) return;
        gating = pkg;
        Intent i = new Intent(this, GateActivity.class)
                .putExtra(GateActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try { startActivity(i); } catch (RuntimeException ignored) { gating = null; }
    }

    private boolean needsPin(String pkg) {
        if (alwaysPin.contains(pkg)) return true;
        Profiles.Mode mode = profiles.mode();
        if (mode == Profiles.Mode.ADULT) return false;
        if (mode == Profiles.Mode.KIDS && kidsApps.contains(pkg)) return false;
        // System pieces without an icon (keyboard, voice overlay, screensaver, volume, Cast) pass.
        return protectedApps.contains(pkg) || launchable(pkg);
    }

    private boolean launchable(String pkg) {
        Boolean b = hasIcon.get(pkg);
        if (b == null) {
            PackageManager pm = getPackageManager();
            b = pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
            hasIcon.put(pkg, b);
        }
        return b;
    }

    @Override
    public void onInterrupt() { }
}
