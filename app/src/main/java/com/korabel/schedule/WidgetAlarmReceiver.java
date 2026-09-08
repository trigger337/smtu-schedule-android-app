package com.korabel.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires on the self-rescheduled widget tick. */
public final class WidgetAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        WidgetUpdater.poke(ctx);
    }
}
