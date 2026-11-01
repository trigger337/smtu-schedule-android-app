package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitLab releases for a newer version, downloads update APKs directly,
 * and launches the system package installer via UpdateProvider.
 */
public final class UpdateChecker {

    public static final String REPO_URL =
            "https://gitlab.com/trigger337/smtu-schedule-android-app";
    public static final String RELEASES_URL = REPO_URL + "/-/releases";

    private static final String P_VERSION = "updVersion";
    private static final String P_APK_URL = "updApkUrl";
    private static final String P_TOASTS = "updToasts";
    private static final int MAX_TOASTS = 3;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static final class ReleaseInfo {
        public final String tag;
        public final String apkUrl;
        public ReleaseInfo(String tag, String apkUrl) {
            this.tag = tag;
            this.apkUrl = apkUrl;
        }
    }

    private UpdateChecker() { }

    /** Background check at app launch; toasts a newer release (max 3 per version). */
    public static void onLaunch(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            ReleaseInfo latest = fetchLatest();
            if (latest == null) return;
            SharedPreferences p = prefs(app);
            p.edit().putString(P_VERSION, latest.tag)
                    .putString(P_APK_URL, latest.apkUrl).apply();
            if (!isNewer(latest.tag, BuildConfig.VERSION_NAME)) {
                p.edit().putInt(P_TOASTS, 0).apply();
                return;
            }
            boolean known = latest.tag.equals(p.getString(P_VERSION, ""));
            int shown = known ? p.getInt(P_TOASTS, 0) : 0;
            if (shown < MAX_TOASTS) {
                p.edit().putInt(P_TOASTS, shown + 1).apply();
                UI.post(() -> Toast.makeText(app,
                        "Доступна версия " + latest.tag + " — кнопка ⓘ в приложении",
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Explicit check from the info dialog; always reports the outcome. */
    public static void checkNow(final Activity activity) {
        Toast.makeText(activity, "Проверка обновлений…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final ReleaseInfo latest = fetchLatest();
            if (latest != null) {
                prefs(activity).edit().putString(P_VERSION, latest.tag)
                        .putString(P_APK_URL, latest.apkUrl).apply();
            }
            UI.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (latest == null) {
                    Toast.makeText(activity, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show();
                } else if (isNewer(latest.tag, BuildConfig.VERSION_NAME)) {
                    showUpdatePrompt(activity, latest);
                } else {
                    Toast.makeText(activity, "Установлена последняя версия", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    public static void showUpdatePrompt(final Activity activity, final ReleaseInfo rel) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle("Доступно обновление");
        b.setMessage("Вышла версия " + rel.tag + ".\n\nОбновить приложение сейчас?");
        if (rel.apkUrl != null && !rel.apkUrl.isEmpty() && !rel.apkUrl.endsWith("/releases/" + rel.tag)) {
            b.setPositiveButton("Обновить", (d, w) -> downloadAndInstall(activity, rel.apkUrl, rel.tag));
        } else {
            b.setPositiveButton("Скачать", (d, w) -> activity.startActivity(new Intent(
                    Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))));
        }
        b.setNegativeButton("Позже", null);
        b.show();
    }

    /** Latest release tag seen by a successful check, or null if never checked. */
    public static String latestKnown(Context ctx) {
        String v = prefs(ctx).getString(P_VERSION, null);
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** Direct APK download URL of the latest known release, or null. */
    public static String latestApkUrl(Context ctx) {
        String u = prefs(ctx).getString(P_APK_URL, null);
        return (u == null || u.isEmpty()) ? null : u;
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

    /** Newest release info across all releases; null on any failure. */
    public static ReleaseInfo fetchLatest() {
        try {
            JSONArray rel = new JSONArray(Smtu.httpGet(BuildConfig.UPDATE_API));
            int[] best = null;
            String bestTag = null;
            String bestUrl = null;
            for (int i = 0; i < rel.length(); i++) {
                JSONObject obj = rel.getJSONObject(i);
                String tag = obj.optString("tag_name", "");
                int[] v = parse(tag);
                if (v != null && (best == null || cmp(v, best) > 0)) {
                    best = v;
                    bestTag = tag;
                    bestUrl = extractApkUrl(obj, tag);
                }
            }
            return bestTag == null ? null : new ReleaseInfo(bestTag, bestUrl);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractApkUrl(JSONObject relObj, String tag) {
        JSONObject assets = relObj.optJSONObject("assets");
        if (assets != null) {
            JSONArray links = assets.optJSONArray("links");
            if (links != null) {
                for (int j = 0; j < links.length(); j++) {
                    JSONObject l = links.optJSONObject(j);
                    if (l == null) continue;
                    String name = l.optString("name", "");
                    String u = l.optString("url", "");
                    if (name.toLowerCase(Locale.US).contains("release") || u.endsWith(".apk")) {
                        return u;
                    }
                }
                for (int j = 0; j < links.length(); j++) {
                    JSONObject l = links.optJSONObject(j);
                    if (l == null) continue;
                    String name = l.optString("name", "");
                    String u = l.optString("url", "");
                    if (name.toLowerCase(Locale.US).contains("apk") || u.contains("download")) {
                        return u;
                    }
                }
            }
        }
        return REPO_URL + "/-/releases/" + tag;
    }

    /** Download APK into internal cache and launch system package installer. */
    public static void downloadAndInstall(final Activity activity, final String apkUrl, final String targetVersion) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(16 * density);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);

        final TextView status = new TextView(activity);
        status.setText("Загрузка обновления " + targetVersion + "…");
        status.setTextSize(14);
        status.setTextColor(activity.getColor(R.color.text_primary));
        box.addView(status);

        final ProgressBar pb = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        box.addView(pb, new LinearLayout.LayoutParams(-1, Math.round(8 * density)));

        final AlertDialog dlg = new AlertDialog.Builder(activity)
                .setTitle("Обновление приложения")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Отмена", null)
                .create();

        final boolean[] cancelled = new boolean[]{false};
        dlg.setButton(AlertDialog.BUTTON_NEGATIVE, "Отмена", (d, w) -> {
            cancelled[0] = true;
            dlg.dismiss();
        });
        dlg.show();

        new Thread(() -> {
            File dest = new File(activity.getCacheDir(), UpdateProvider.FILE_NAME);
            HttpURLConnection conn = null;
            try {
                String curUrl = apkUrl;
                for (int redirect = 0; redirect < 5; redirect++) {
                    conn = (HttpURLConnection) new URL(curUrl).openConnection(Proxy.NO_PROXY);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "SMTU-Schedule-Android/" + BuildConfig.VERSION_NAME);
                    conn.setInstanceFollowRedirects(true);
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM
                            || code == 307 || code == 308) {
                        curUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        continue;
                    }
                    if (code != HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP " + code + " " + conn.getResponseMessage());
                    }
                    break;
                }

                int total = conn.getContentLength();
                if (total > 0) {
                    UI.post(() -> pb.setIndeterminate(false));
                }

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int r;
                    int cur = 0;
                    while ((r = in.read(buf)) != -1) {
                        if (cancelled[0]) {
                            dest.delete();
                            return;
                        }
                        out.write(buf, 0, r);
                        cur += r;
                        if (total > 0) {
                            final int progress = (int) (cur * 100L / total);
                            final int kbs = cur / 1024;
                            final int totalKbs = total / 1024;
                            UI.post(() -> {
                                pb.setProgress(progress);
                                status.setText("Загружено " + kbs + " из " + totalKbs + " КБ…");
                            });
                        }
                    }
                }

                UI.post(() -> {
                    dlg.dismiss();
                    if (!cancelled[0]) {
                        installApk(activity, dest);
                    }
                });
            } catch (final Exception e) {
                if (dest.exists()) dest.delete();
                UI.post(() -> {
                    dlg.dismiss();
                    if (!cancelled[0]) {
                        Toast.makeText(activity, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /** Trigger Android PackageInstaller via content:// URI. */
    public static void installApk(Activity activity, File dest) {
        if (!dest.exists()) return;
        try {
            Uri apkUri = Uri.parse("content://" + activity.getPackageName() + UpdateProvider.AUTHORITY_SUFFIX + "/" + UpdateProvider.FILE_NAME);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Не удалось запустить установку: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("sched", Context.MODE_PRIVATE);
    }
}
