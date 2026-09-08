package com.korabel.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitLab releases for a newer version. Silent at launch except that a
 * newer release is toasted on at most 3 launches per version; then it stays
 * quiet until yet another version appears. The info dialog always shows the
 * last known state and can re-check on demand.
 */
public final class UpdateChecker {

    public static final String REPO_URL =
            "https://gitlab.com/trigger337/smtu-schedule-android-app";
    public static final String RELEASES_URL = REPO_URL + "/-/releases";

    private static final String P_VERSION = "updVersion";
    private static final String P_TOASTS = "updToasts";
    private static final int MAX_TOASTS = 3;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private UpdateChecker() { }

    /** Background check at app launch; toasts a newer release (max 3 per version). */
    public static void onLaunch(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            String latest = fetchLatest();
            if (latest == null) return;
            SharedPreferences p = prefs(app);
            if (!isNewer(latest, BuildConfig.VERSION_NAME)) {
                p.edit().putString(P_VERSION, latest).putInt(P_TOASTS, 0).apply();
                return;
            }
            boolean known = latest.equals(p.getString(P_VERSION, ""));
            int shown = known ? p.getInt(P_TOASTS, 0) : 0;
            if (shown < MAX_TOASTS) {
                p.edit().putString(P_VERSION, latest).putInt(P_TOASTS, shown + 1).apply();
                UI.post(() -> Toast.makeText(app,
                        "Доступна версия " + latest + " — кнопка ⓘ в приложении",
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Explicit check from the info dialog; always reports the outcome. */
    public static void checkNow(Context ctx) {
        final Context app = ctx.getApplicationContext();
        Toast.makeText(app, "Проверка обновлений…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String latest = fetchLatest();
            if (latest != null) prefs(app).edit().putString(P_VERSION, latest).apply();
            UI.post(() -> {
                if (latest == null)
                    Toast.makeText(app, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show();
                else if (isNewer(latest, BuildConfig.VERSION_NAME))
                    Toast.makeText(app, "Доступна версия " + latest, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(app, "Установлена последняя версия", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    /** Latest release tag seen by a successful check, or null if never checked. */
    public static String latestKnown(Context ctx) {
        String v = prefs(ctx).getString(P_VERSION, null);
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** Semantic compare of x.y.z tags; true if a is newer than b. */
    public static boolean isNewer(String a, String b) {
        int[] x = parse(a), y = parse(b);
        if (x == null || y == null) return false;
        return cmp(x, y) > 0;
    }

    private static int cmp(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i] ? 1 : -1;
        return 0;
    }

    private static int[] parse(String v) {
        if (v == null) return null;
        Matcher m = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?$").matcher(v.trim());
        if (!m.matches()) return null;
        return new int[]{
                Integer.parseInt(m.group(1)),
                m.group(2) == null ? 0 : Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3))};
    }

    /** Newest numeric tag across all releases; null on any failure. */
    private static String fetchLatest() {
        try {
            JSONArray rel = new JSONArray(Smtu.httpGet(BuildConfig.UPDATE_API));
            int[] best = null;
            String bestTag = null;
            for (int i = 0; i < rel.length(); i++) {
                String tag = rel.getJSONObject(i).optString("tag_name", "");
                int[] v = parse(tag);
                if (v != null && (best == null || cmp(v, best) > 0)) { best = v; bestTag = tag; }
            }
            return bestTag;
        } catch (Exception e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("sched", Context.MODE_PRIVATE);
    }
}
