package com.korabel.schedule;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;

/**
 * Debug-only test harness: hosts the app's widgets through the real
 * AppWidgetHost pipeline (bind dialog included), in a plain window that
 * uiautomator can dump. Extras: "widget" 0=timer 1=name 2=agenda,
 * "orient" 0=horizontal (default) 1=vertical — the shape is pushed through
 * updateAppWidgetOptions so providers pick the layout exactly like a
 * launcher resize would. Not shipped in release builds.
 */
public final class WidgetHostActivity extends Activity {

    private AppWidgetHost host;
    private AppWidgetManager awm;
    private int pendingId = -1;
    private int pendingWhich;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        awm = AppWidgetManager.getInstance(this);
        host = new AppWidgetHost(this, 5001);
        final int which = getIntent().getIntExtra("widget", 0);
        ComponentName provider = new ComponentName(this,
                which == 1 ? NameWidgetProvider.class
                : which == 2 ? AgendaWidgetProvider.class
                : TimerWidgetProvider.class);
        int[] existing = host.getAppWidgetIds();
        int id = -1;
        for (int e : existing)
            if (awm.getAppWidgetInfo(e).provider.equals(provider)) id = e;
        if (id >= 0) { show(id, which); return; }
        id = host.allocateAppWidgetId();
        if (awm.bindAppWidgetIdIfAllowed(id, provider)) { show(id, which); return; }
        pendingId = id;
        pendingWhich = which;
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
        startActivityForResult(i, 1);
    }

    @Override protected void onStart() {
        super.onStart();
        host.startListening();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && pendingId >= 0) show(pendingId, pendingWhich);
        else finish();
    }

    private void show(int id, int which) {
        int w = which == 1 ? 210 : which == 2 ? 280 : 70;   // dp, ~one cell = 70
        int h = which == 0 ? 70 : (which == 1 ? 70 : 140);
        if (getIntent().getIntExtra("orient", 0) == 1) {
            int t = w; w = h; h = t;
            if (which == 1) { w = 40; h = 180; }           // name vertical = 1x3
        }

        Bundle o = new Bundle();
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h);
        awm.updateAppWidgetOptions(id, o);

        // Push a fresh render synchronously: host views don't repaint for
        // updates that arrive while they are already on screen.
        awm.updateAppWidget(id,
                which == 1 ? NameWidgetProvider.render(this, awm, id)
                : which == 2 ? AgendaWidgetProvider.render(this, awm, id)
                : TimerWidgetProvider.render(this));

        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        AppWidgetHostView v = host.createView(this, id, info);

        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF33691E);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        root.addView(v, new LinearLayout.LayoutParams(
                Math.round(w * dm.density), Math.round(h * dm.density)));
        setContentView(root);
    }

    @Override protected void onStop() {
        super.onStop();
        if (host != null) host.stopListening();
    }
}
