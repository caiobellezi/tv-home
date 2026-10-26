package com.mihir.tvhome;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/** Locks the profiles whenever the TV goes to standby (screen off can only be caught at runtime). */
public class HomeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { new Profiles(c).lock(); }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
}
