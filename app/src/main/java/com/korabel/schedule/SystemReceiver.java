package com.korabel.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-renders widgets after boot, time/date/timezone changes and app updates. */
public final class SystemReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        WidgetUpdater.poke(ctx);
    }
}
