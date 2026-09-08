package com.korabel.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.korabel.schedule.Smtu.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 2x3 agenda: a «Сейчас» block (subject, time, room, thin draining bar)
 * while a lesson runs, then «Далее» rows with the upcoming slots. After the
 * day is over it falls over to the next study day, with the day in the
 * header. Tapping opens the app.
 */
public final class AgendaWidgetProvider extends AppWidgetProvider {

    private static final int[] ROW = {R.id.wa_row1, R.id.wa_row2, R.id.wa_row3};
    private static final int[] ROW_TIME = {R.id.wa_r1_time, R.id.wa_r2_time, R.id.wa_r3_time};
    private static final int[] ROW_SUBJ = {R.id.wa_r1_subj, R.id.wa_r2_subj, R.id.wa_r3_subj};
    private static final int[] ROW_ROOM = {R.id.wa_r1_room, R.id.wa_r2_room, R.id.wa_r3_room};

    @Override public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        Context app = ctx.getApplicationContext();
        for (int id : ids) awm.updateAppWidget(id, render(app));
        WidgetUpdater.schedule(app);
    }

    static RemoteViews render(Context app) {
        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget_agenda);
        Now.State st = Now.load(app);

        if (st.kind == Now.NONE) {
            rv.setTextViewText(R.id.wa_header, st.gid == null ? "НЕТ ГРУППЫ" : "ПАР НЕТ");
            block(rv, false);
            rows(rv, Collections.<Slot>emptyList());
            click(rv, app);
            return rv;
        }

        List<Slot> upcoming;
        if (st.kind == Now.ONGOING) {
            rv.setTextViewText(R.id.wa_header, "СЕЙЧАС");
            rv.setTextViewText(R.id.wa_subject, st.current.subject);
            rv.setTextViewText(R.id.wa_meta,
                    Now.compactTime(st.current.time) + " · " + Now.roomFirst(st.current.room));
            int total = Now.endMin(st.current) - Now.startMin(st.current);
            rv.setInt(R.id.wa_bar, "setProgress", total > 0
                    ? Math.max(0, Math.min(100, st.remainMin * 100 / total)) : 0);
            rv.setViewVisibility(R.id.wa_subject, View.VISIBLE);
            rv.setViewVisibility(R.id.wa_meta, View.VISIBLE);
            rv.setViewVisibility(R.id.wa_bar, View.VISIBLE);
            rv.setViewVisibility(R.id.wa_nh, View.VISIBLE);
            rv.setTextViewText(R.id.wa_nh, "ДАЛЕЕ");
            upcoming = afterNow(st.today, st.nowMin);
        } else if (st.kind == Now.AFTER) {
            String day = Now.isTomorrow(st) ? "ЗАВТРА"
                    : Now.weekdayName(st.nextDay).toUpperCase(new Locale("ru"));
            rv.setTextViewText(R.id.wa_header, day);
            block(rv, false);
            upcoming = Now.slotsOf(app, Smtu.loadCacheSync(app, false, st.gid), st.nextDay);
            Collections.sort(upcoming, (a, b) -> Now.startMin(a) - Now.startMin(b));
        } else {                                   // BREAK / BEFORE: list the rest of today
            rv.setTextViewText(R.id.wa_header, "ДАЛЕЕ");
            block(rv, false);
            upcoming = afterNow(st.today, st.nowMin);
        }
        rows(rv, upcoming);
        click(rv, app);
        return rv;
    }

    private static List<Slot> afterNow(List<Slot> today, int nowMin) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : today)
            if (Now.startMin(s) > nowMin) out.add(s);
        return out;
    }

    /** Show/hide the «Сейчас» block (subject, meta line, progress bar). */
    private static void block(RemoteViews rv, boolean show) {
        int v = show ? View.VISIBLE : View.GONE;
        rv.setViewVisibility(R.id.wa_subject, v);
        rv.setViewVisibility(R.id.wa_meta, v);
        rv.setViewVisibility(R.id.wa_bar, v);
        rv.setViewVisibility(R.id.wa_nh, v);
    }

    private static void rows(RemoteViews rv, List<Slot> list) {
        for (int i = 0; i < ROW.length; i++) {
            boolean has = i < list.size();
            rv.setViewVisibility(ROW[i], has ? View.VISIBLE : View.GONE);
            if (!has) continue;
            Slot s = list.get(i);
            rv.setTextViewText(ROW_TIME[i], s.time.length() >= 5 ? s.time.substring(0, 5) : s.time);
            rv.setTextViewText(ROW_SUBJ[i], s.subject);
            rv.setTextViewText(ROW_ROOM[i], Now.roomFirst(s.room));
        }
    }

    private static void click(RemoteViews rv, Context app) {
        rv.setOnClickPendingIntent(R.id.wa_root, PendingIntent.getActivity(app, 0,
                new Intent(app, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}
