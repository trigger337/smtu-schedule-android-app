package com.korabel.schedule;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.korabel.schedule.Smtu.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Day agenda. Default shape is 4x2 horizontal: a bitmap text panel (half the
 * card — adaptive row count, subjects wrap around a "◦ teacher" tail, earlier
 * rows wrap first) beside a graphic tile where the hour timeline fills the
 * whole area with just the countdown minutes inside. A taller-than-wide
 * widget flips to the 2x4 card with the two halves stacked, the timeline
 * running upward. Tap refreshes, double tap opens the app.
 */
public final class AgendaWidgetProvider extends AppWidgetProvider {

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
                h ? R.layout.widget_agenda_h : R.layout.widget_agenda);
        Now.State st = Now.load(app);
        List<Slot> upcoming = upcoming(app, st);
        float d = app.getResources().getDisplayMetrics().density;

        int half, full;
        if (h) {
            full = Math.round(WidgetArt.heightDp(o, 110) * d);
            half = Math.round((WidgetArt.widthDp(o, 280) - 1) / 2f * d);   // 1dp divider
            rv.setImageViewBitmap(R.id.wa_panel,
                    WidgetArt.agendaPanel(app, st, upcoming, half, full));
            rv.setImageViewBitmap(R.id.wa_tile,
                    WidgetArt.barTile(app, st, half, full, false));
        } else {
            full = Math.round(WidgetArt.widthDp(o, 110) * d);
            half = Math.round((WidgetArt.heightDp(o, 250) - 1) / 2f * d);
            rv.setImageViewBitmap(R.id.wa_panel,
                    WidgetArt.agendaPanel(app, st, upcoming, full, half));
            rv.setImageViewBitmap(R.id.wa_tile,
                    WidgetArt.barTile(app, st, full, half, true));
        }
        rv.setContentDescription(R.id.wa_panel, description(st, upcoming));
        rv.setOnClickPendingIntent(R.id.wa_root, WidgetClickReceiver.pending(app));
        return rv;
    }

    /** Slots after now: today's rest, or the next study day's list. */
    private static List<Slot> upcoming(Context app, Now.State st) {
        if (st.kind == Now.AFTER) {
            List<Slot> of = Now.slotsOf(app, Smtu.loadCacheSync(app, false, st.gid), st.nextDay);
            Collections.sort(of, (a, b) -> Now.startMin(a) - Now.startMin(b));
            return of;
        }
        if (st.kind == Now.NONE) return Collections.emptyList();
        List<Slot> out = new ArrayList<>();
        for (Slot s : st.today)
            if (Now.startMin(s) > st.nowMin) out.add(s);
        return out;
    }

    /** TalkBack summary of the bitmap panel, which it cannot read itself. */
    private static String description(Now.State st, List<Slot> upcoming) {
        StringBuilder sb = new StringBuilder();
        if (st.kind == Now.ONGOING) sb.append("Сейчас: ").append(st.current.subject).append(". ");
        sb.append(upcoming.isEmpty() ? "Пар больше нет"
                : "Далее " + upcoming.size() + " " + plural(upcoming.size()));
        for (int i = 0; i < upcoming.size() && i < 4; i++) {
            Slot s = upcoming.get(i);
            sb.append(". ").append(s.time, 0, Math.min(5, s.time.length()))
                    .append(' ').append(s.subject);
        }
        return sb.toString();
    }

    private static String plural(int n) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return "пара";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "пары";
        return "пар";
    }
}
