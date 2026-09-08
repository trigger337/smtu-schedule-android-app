package com.korabel.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.korabel.schedule.Smtu.Slot;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 1x2 "current / next couple" card: a colored stripe on the left edge, a
 * state label, the bold subject, the time range, and a countdown line that
 * ticks per-minute near the boundary or shows the room otherwise. Tapping
 * opens the app.
 */
public final class NameWidgetProvider extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        Context app = ctx.getApplicationContext();
        for (int id : ids) awm.updateAppWidget(id, render(app));
        WidgetUpdater.schedule(app);
    }

    static RemoteViews render(Context app) {
        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget_name);
        String gid = app.getSharedPreferences("sched", Context.MODE_PRIVATE).getString("groupId", null);
        List<Slot> slots = gid == null ? Collections.<Slot>emptyList()
                : Smtu.loadCacheSync(app, false, gid);
        Now.State st = Now.compute(app, slots);

        Slot shown;
        String label, extra;
        int accent;
        switch (st.kind) {
            case Now.ONGOING:
                shown = st.current;
                label = "Сейчас";
                extra = "до конца " + st.remainMin + " мин";
                accent = R.color.w_ring_lesson;
                break;
            case Now.BREAK:
            case Now.BEFORE:
                shown = st.next;
                label = "Следующая пара";
                extra = st.remainMin <= 90 ? inMin(st.remainMin) : roomOf(st.next);
                accent = R.color.w_ring_break;
                break;
            case Now.AFTER:
                shown = st.next;
                label = TimerWidgetProvider.isTomorrow(st) ? "Завтра"
                        : new SimpleDateFormat("EEEE", new Locale("ru")).format(st.nextDay.getTime());
                extra = roomOf(st.next);
                accent = R.color.w_ring_break;
                break;
            default:
                rv.setTextViewText(R.id.wn_label, gid == null ? "НЕТ ГРУППЫ" : "ПАР НЕТ");
                rv.setTextViewText(R.id.wn_subject, "");
                rv.setTextViewText(R.id.wn_time, "");
                rv.setTextViewText(R.id.wn_extra, "");
                stripe(rv, 0);
                rv.setOnClickPendingIntent(R.id.wn_root, click(app));
                return rv;
        }
        rv.setTextViewText(R.id.wn_label, label.toUpperCase(new Locale("ru")));
        rv.setTextViewText(R.id.wn_subject, shown.subject);
        rv.setTextViewText(R.id.wn_time, shown.time.replace(" ", "").replace('-', '–'));
        rv.setTextViewText(R.id.wn_extra, extra);
        rv.setTextColor(R.id.wn_label, app.getColor(accent));
        rv.setTextColor(R.id.wn_extra,
                app.getColor(st.kind == Now.AFTER ? R.color.w_sub : accent));
        stripe(rv, st.kind == Now.ONGOING ? 1 : 2);
        rv.setOnClickPendingIntent(R.id.wn_root, click(app));
        return rv;
    }

    /** "через 2 ч 15 мин" / "через 25 мин". */
    private static String inMin(int m) {
        if (m >= 60) {
            int h = m / 60, r = m % 60;
            return "через " + h + " ч" + (r > 0 ? " " + r + " мин" : "");
        }
        return "через " + m + " мин";
    }

    /** "509 Корпус Г" -> "ауд. 509"; empty when the slot has no room. */
    private static String roomOf(Slot s) {
        if (s.room == null || s.room.isEmpty()) return "";
        int sp = s.room.indexOf(' ');
        return "ауд. " + (sp > 0 ? s.room.substring(0, sp) : s.room);
    }

    private static void stripe(RemoteViews rv, int which) {
        rv.setViewVisibility(R.id.wn_stripe_b, which == 1 ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.wn_stripe_y, which == 2 ? View.VISIBLE : View.GONE);
    }

    private static PendingIntent click(Context app) {
        return PendingIntent.getActivity(app, 0, new Intent(app, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
