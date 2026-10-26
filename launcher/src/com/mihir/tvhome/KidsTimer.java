package com.mihir.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Alarms for the kids' session: a warning 5 minutes before, then back to the locked calendar. */
public class KidsTimer extends BroadcastReceiver {

    static final String ACTION_WARN = "com.mihir.tvhome.KIDS_WARN";
    static final String ACTION_END = "com.mihir.tvhome.KIDS_END";

    @Override
    public void onReceive(Context c, Intent i) {
        Profiles p = new Profiles(c);
        if (ACTION_WARN.equals(i.getAction())) {
            if (p.mode() == Profiles.Mode.KIDS) {
                Toast.makeText(c, "Faltam 5 minutos de TV", Toast.LENGTH_LONG).show();
            }
        } else if (ACTION_END.equals(i.getAction())) {
            p.lock();
            goHome(c);
        }
    }

    /** Brings the launcher to the front, which shows the locked calendar. */
    static void goHome(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(c.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(home);
        } catch (RuntimeException ignored) { }
    }
}
