package com.korabel.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

/**
 * 1x1 radial countdown: a draining blue ring while a lesson runs, a filling
 * yellow ring during a break, and the next lesson time once the day is over.
 * Tapping opens the app.
 */
public final class TimerWidgetProvider extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        Context app = ctx.getApplicationContext();
        for (int id : ids) awm.updateAppWidget(id, render(app));
        WidgetUpdater.schedule(app);
    }

    static RemoteViews render(Context app) {
        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget_timer);
        Now.State st = Now.load(app);

        String main = "—";
        String sub = "";
        switch (st.kind) {
            case Now.ONGOING:
                rv.setViewVisibility(R.id.wt_ring_b, View.VISIBLE);
                rv.setViewVisibility(R.id.wt_ring_y, View.GONE);
                int total = Now.endMin(st.current) - Now.startMin(st.current);
                rv.setInt(R.id.wt_ring_b, "setProgress", pct(st.remainMin, total));
                main = String.valueOf(st.remainMin);
                sub = "мин";
                break;
            case Now.BREAK:
            case Now.BEFORE:
                rv.setViewVisibility(R.id.wt_ring_b, View.GONE);
                rv.setViewVisibility(R.id.wt_ring_y, View.VISIBLE);
                rv.setInt(R.id.wt_ring_y, "setProgress", pct(st.gapMin - st.remainMin, st.gapMin));
                if (st.remainMin > 90) {          // far away: static start time, no ticking needed
                    main = st.next.time.substring(0, 5);
                    sub = "начало";
                } else {
                    main = String.valueOf(st.remainMin);
                    sub = "до пары";
                }
                break;
            case Now.AFTER:
                rv.setViewVisibility(R.id.wt_ring_b, View.GONE);
                rv.setViewVisibility(R.id.wt_ring_y, View.GONE);
                main = st.next.time.substring(0, 5);
                sub = Now.isTomorrow(st) ? "завтра" : Now.weekdayName(st.nextDay);
                break;
            default:
                sub = st.gid == null ? "нет группы" : "нет пар";
        }
        rv.setTextViewText(R.id.wt_main, main);
        rv.setTextViewText(R.id.wt_sub, sub);
        rv.setOnClickPendingIntent(R.id.wt_root, PendingIntent.getActivity(app, 0,
                new Intent(app, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return rv;
    }

    private static int pct(int part, int whole) {
        if (whole <= 0) return 0;
        return Math.max(0, Math.min(100, part * 100 / whole));
    }
}
