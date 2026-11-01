package com.korabel.schedule;

import android.app.Application;
import android.content.res.Configuration;

/**
 * Application entry point for process-level events.
 * Listens for system configuration changes (e.g. dark/light theme switch)
 * and keeps bound home screen widgets synchronized in real time.
 */
public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WidgetUpdater.poke(this);
    }
}
