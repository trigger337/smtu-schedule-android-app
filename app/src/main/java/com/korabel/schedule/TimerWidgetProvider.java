package com.korabel.schedule;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

/**
 * 1x1 radial countdown: a draining blue ring while a lesson runs, a filling
 * yellow ring during a break, and the next lesson time once the day is over.
 * Text and progress rules live in WidgetArt so the agenda's ring tile stays
 * in sync. Tap refreshes, double tap opens the app.
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

        int col = WidgetArt.ringColorRes(st);
        rv.setViewVisibility(R.id.wt_ring_b, col == R.color.w_ring_lesson ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.wt_ring_y, col == R.color.w_ring_break ? View.VISIBLE : View.GONE);
        if (col != 0) rv.setInt(
                col == R.color.w_ring_lesson ? R.id.wt_ring_b : R.id.wt_ring_y,
                "setProgress", WidgetArt.ringPct(st));

        String[] t = WidgetArt.ringText(st);
        rv.setTextViewText(R.id.wt_main, t[0]);
        rv.setTextViewText(R.id.wt_sub, t[1]);
        rv.setOnClickPendingIntent(R.id.wt_root, WidgetClickReceiver.pending(app));
        return rv;
    }
}
