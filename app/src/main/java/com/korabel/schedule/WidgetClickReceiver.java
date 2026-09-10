package com.korabel.schedule;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Tap routing for every widget: a single tap refreshes them; a second tap
 * within TAP_WINDOW opens the app. RemoteViews cannot see gestures, so the
 * double tap is inferred from two broadcasts arriving close together (the
 * first tap of a double also refreshes — harmless). Timestamps use
 * elapsedRealtime so they survive clock changes.
 */
public final class WidgetClickReceiver extends BroadcastReceiver {

    private static final long TAP_WINDOW = 350;   // ms
    private static final String P_TAP = "widgetTap";

    @Override public void onReceive(Context ctx, Intent intent) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("sched", Context.MODE_PRIVATE);
        long now = SystemClock.elapsedRealtime();
        long last = p.getLong(P_TAP, 0);
        if (last > 0 && now - last < TAP_WINDOW) {
            p.edit().putLong(P_TAP, 0).apply();
            Intent open = new Intent(app, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(open);
        } else {
            p.edit().putLong(P_TAP, now).apply();
            WidgetUpdater.poke(app);
        }
    }

    /** The one shared broadcast PendingIntent set on all widget roots. */
    static PendingIntent pending(Context app) {
        return PendingIntent.getBroadcast(app, 100,
                new Intent(app, WidgetClickReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
