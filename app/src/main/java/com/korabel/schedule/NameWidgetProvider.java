package com.korabel.schedule;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.korabel.schedule.Smtu.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Current / next couple" card: state label, bold subject, teacher, time
 * range and a countdown that ticks per-minute near the boundary or shows the
 * room otherwise. Default shape is 3x1 horizontal; a taller-than-wide widget
 * flips to the 1x3 vertical card, whose text runs top-to-bottom in columns
 * of upright letters, first column rightmost. Both carry the hour timeline
 * bar. Tap refreshes, double tap opens the app.
 */
public final class NameWidgetProvider extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        Context app = ctx.getApplicationContext();
        for (int id : ids) awm.updateAppWidget(id, render(app, awm, id));
        WidgetUpdater.schedule(app);
    }

    @Override public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager awm,
                                                    int id, Bundle newOptions) {
        Context app = ctx.getApplicationContext();
        awm.updateAppWidget(id, render(app, awm, id));
    }

    static RemoteViews render(Context app, AppWidgetManager awm, int id) {
        Bundle o = awm.getAppWidgetOptions(id);
        boolean h = WidgetArt.isHorizontal(o);
        RemoteViews rv = new RemoteViews(app.getPackageName(),
                h ? R.layout.widget_name_h : R.layout.widget_name);
        Now.State st = Now.load(app);
        float d = app.getResources().getDisplayMetrics().density;

        String label, extra = "";
        int accent;
        Slot shown;
        if (st.kind == Now.NONE) {
            label = st.gid == null ? "НЕТ ГРУППЫ" : "ПАР НЕТ";
            accent = R.color.w_sub;
            shown = null;
        } else {
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
                    extra = st.remainMin <= 90 ? inMin(st.remainMin) : audOf(st.next);
                    accent = R.color.w_ring_break;
                    break;
                default:                              // AFTER
                    shown = st.next;
                    label = Now.isTomorrow(st) ? "Завтра" : Now.weekdayName(st.nextDay);
                    extra = audOf(st.next);
                    accent = R.color.w_ring_break;
                    break;
            }
            label = label.toUpperCase(new Locale("ru"));
        }

        if (h) {
            rv.setTextViewText(R.id.wn_label, label);
            rv.setTextColor(R.id.wn_label, app.getColor(accent));
            rv.setTextViewText(R.id.wn_subject, shown == null ? "" : shown.subject);
            rv.setTextViewText(R.id.wn_time, shown == null ? "" : Now.compactTime(shown.time));
            String teacher = shown == null ? "" : shown.teacher;
            rv.setTextViewText(R.id.wn_teacher, teacher);
            rv.setViewVisibility(R.id.wn_teacher,
                    teacher.isEmpty() ? View.GONE : View.VISIBLE);
            rv.setTextViewText(R.id.wn_extra, extra);
            rv.setTextColor(R.id.wn_extra,
                    app.getColor(st.kind == Now.AFTER ? R.color.w_sub : accent));

            // the bar shares its row with the extra line
            int barDp = WidgetArt.widthDp(o, 180) - 14 - (extra.isEmpty() ? 0 : 74);
            rv.setImageViewBitmap(R.id.wn_bar,
                    WidgetArt.timeline(app, st, Math.round(Math.max(barDp, 40) * d),
                            Math.round(9 * d), false));
        } else {
            List<WidgetArt.VLine> lines = new ArrayList<>();
            if (shown == null) {
                lines.add(new WidgetArt.VLine(label, 10, true, R.color.w_sub, 2));
            } else {
                lines.add(new WidgetArt.VLine(shown.subject, 11, true, R.color.w_text, 2));
                if (!shown.teacher.isEmpty())
                    lines.add(new WidgetArt.VLine(shown.teacher, 8, false, R.color.w_sub, 2));
                String meta = label + " \u00B7 " + Now.compactTime(shown.time)
                        + (extra.isEmpty() ? "" : " \u00B7 " + extra);
                lines.add(new WidgetArt.VLine(meta, 8, false, R.color.w_sub, 2));
                rv.setContentDescription(R.id.wn_text,
                        label + ": " + shown.subject
                        + (shown.teacher.isEmpty() ? "" : ", " + shown.teacher));
            }
            int textW = Math.round((WidgetArt.widthDp(o, 40) - 12) * d);
            int textH = Math.round((WidgetArt.heightDp(o, 180) - 27) * d);
            rv.setImageViewBitmap(R.id.wn_text, WidgetArt.vtext(app, textW, textH, lines));
            rv.setImageViewBitmap(R.id.wn_bar,
                    WidgetArt.timeline(app, st, Math.round((WidgetArt.widthDp(o, 40) - 12) * d),
                            Math.round(10 * d), false));
        }
        rv.setOnClickPendingIntent(R.id.wn_root, WidgetClickReceiver.pending(app));
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

    /** "ауд. 509"; empty when the slot has no room. */
    private static String audOf(Slot s) {
        String r = Now.roomFirst(s.room);
        return r.isEmpty() ? "" : "ауд. " + r;
    }
}
