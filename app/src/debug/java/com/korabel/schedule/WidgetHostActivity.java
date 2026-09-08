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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        awm = AppWidgetManager.getInstance(this);
        host = new AppWidgetHost(this, 5001);
        host.startListening();
        ComponentName provider = new ComponentName(this, TimerWidgetProvider.class);
        int[] existing = awm.getAppWidgetIds(provider);
        if (existing.length > 0) { show(existing[0], 160); return; }
        int id = host.allocateAppWidgetId();
        if (awm.bindAppWidgetIdIfAllowed(id, provider)) { show(id, 160); return; }
        pendingId = id;
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
        startActivityForResult(i, 1);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && pendingId >= 0) show(pendingId, 160);
        else finish();
    }

    private void show(int id, int dp) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        AppWidgetHostView v = host.createView(this, id, info);
        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF33691E);
        int px = Math.round(dp * getResources().getDisplayMetrics().density);
        root.addView(v, new LinearLayout.LayoutParams(px, px));
        setContentView(root);
    }

    @Override protected void onStop() {
        super.onStop();
        if (host != null) host.stopListening();
    }
}
