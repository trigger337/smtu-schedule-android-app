package com.korabel.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import com.korabel.schedule.Smtu.Slot;

/**
 * Renders every bound widget and keeps a single self-rescheduling alarm so
 * they stay current even with the app process dead. Per-minute ticks happen
 * only while a lesson runs or a short break counts down; otherwise the alarm
 * fires at the next state boundary (or midnight).
 */
public final class WidgetUpdater {

    private static final long STALE_MS = 12 * 3600000L;   // refetch schedule if cache older
    private static final long FETCH_THROTTLE_MS = 3600000L;
    private static final String P_GID = "groupId";

    private WidgetUpdater() { }

    /** Re-render every bound widget and reschedule the next tick. */
    public static void poke(Context ctx) {
        render(ctx);
        schedule(ctx);
    }

    /** Push fresh RemoteViews to every bound widget. */
    public static void render(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager awm = AppWidgetManager.getInstance(app);
        int[] ids = awm.getAppWidgetIds(new ComponentName(app, TimerWidgetProvider.class));
        for (int id : ids) awm.updateAppWidget(id, TimerWidgetProvider.render(app));
        int[] nameIds = awm.getAppWidgetIds(new ComponentName(app, NameWidgetProvider.class));
        for (int id : nameIds) awm.updateAppWidget(id, NameWidgetProvider.render(app));
        if (ids.length + nameIds.length > 0) maybeRefresh(app);
    }

    /** Background schedule refetch when the cache is stale (throttled hourly). */
    private static void maybeRefresh(final Context app) {
        final SharedPreferences p = app.getSharedPreferences("sched", Context.MODE_PRIVATE);
        final String gid = p.getString(P_GID, null);
        if (gid == null) return;
        File cache = new File(app.getFilesDir(), "g_" + gid + ".json");
        long now = System.currentTimeMillis();
        if (cache.exists() && now - cache.lastModified() < STALE_MS) return;
        if (now - p.getLong("widgetFetch", 0) < FETCH_THROTTLE_MS) return;
        p.edit().putLong("widgetFetch", now).apply();
        Smtu.schedule(app, false, gid, (result, error) -> {
            if (result != null && !result.isEmpty()) poke(app);
        });
    }

    /** Alarm for the next state change; per-minute only during a lesson / short break. */
    static void schedule(Context ctx) {
        final Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("sched", Context.MODE_PRIVATE);
        String gid = p.getString(P_GID, null);
        List<Slot> slots = gid == null ? Collections.<Slot>emptyList()
                : Smtu.loadCacheSync(app, false, gid);
        Now.State st = Now.compute(app, slots);

        long now = System.currentTimeMillis();
        long tick = Long.MAX_VALUE;
        switch (st.kind) {
            case Now.ONGOING:
                tick = now + 60000L;
                break;
            case Now.BREAK:
            case Now.BEFORE:
                tick = st.remainMin <= 90 ? now + 60000L
                        : now + (long) st.remainMin * 60000L;
                break;
            case Now.AFTER:
                tick = st.nextDay.getTimeInMillis() + (long) Now.startMin(st.next) * 60000L;
                break;
            default:
                return; // nothing to wait for
        }
        // midnight rollover always flips day-dependent labels
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 1);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        midnight.add(Calendar.DAY_OF_YEAR, 1);
        tick = Math.min(tick, midnight.getTimeInMillis());

        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(app, 0,
                new Intent(app, WidgetAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(tick, now + 15000L), pi);
    }
}
