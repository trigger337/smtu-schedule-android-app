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
 * uiautomator can dump. Not shipped in release builds.
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
        int[] existing = awm.getAppWidgetIds(provider);
        if (existing.length > 0) { show(existing[0], which); return; }
        int id = host.allocateAppWidgetId();
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
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        AppWidgetHostView v = host.createView(this, id, info);
        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF33691E);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.round((which == 1 ? 110 : which == 2 ? 180 : 160) * dm.density);
        int h = Math.round((which == 1 ? 200 : which == 2 ? 240 : 160) * dm.density);
        root.addView(v, new LinearLayout.LayoutParams(w, h));
        setContentView(root);
    }

    @Override protected void onStop() {
        super.onStop();
        if (host != null) host.stopListening();
    }
}
