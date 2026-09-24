package com.korabel.schedule;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Tap routing for every widget: a single tap refreshes them; a second tap
 * within TAP_WINDOW opens the app. RemoteViews cannot see gestures, so the
 * double tap is inferred from two broadcasts arriving close together (the
 * first tap of a double also refreshes — harmless). Timestamps use
 * elapsedRealtime so they survive clock changes.
 */
public final class WidgetClickReceiver extends BroadcastReceiver {

    private static final long TAP_WINDOW = 800;   // ms: accommodates launcher IPC dispatch delay and natural double-tap cadence
    private static final String P_TAP = "widgetTap";
    private static long lastTap = 0;

    @Override public void onReceive(Context ctx, Intent intent) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("sched", Context.MODE_PRIVATE);
        long now = SystemClock.elapsedRealtime();
        long last = Math.max(lastTap, p.getLong(P_TAP, 0));
        if (last > 0 && now - last < TAP_WINDOW) {
            lastTap = 0;
            p.edit().putLong(P_TAP, 0).apply();
            Intent open = new Intent(app, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Bundle b = null;
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                b = opts.toBundle();
            }
            app.startActivity(open, b);
        } else {
            lastTap = now;
            p.edit().putLong(P_TAP, now).apply();
            WidgetUpdater.poke(app);
        }
    }

    /** The one shared broadcast PendingIntent set on all widget roots. */
    static PendingIntent pending(Context app) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(app, 100,
                new Intent(app, WidgetClickReceiver.class), flags);
    }
}
