package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Building floor plans. А/Б/У have one PDF per building (official plans from
 * smtu.ru/ru/page/201, hosted on isu.smtu.ru behind a redirect), downloaded
 * once into filesDir and rendered page-by-page with the framework
 * PdfRenderer; page N = floor N (verified against the «N ЭТАЖ» stamps in
 * the scans). Г/М have no published plans — address fallback dialog.
 */
final class Maps {

    static final class Building {
        final String letter, address, url;
        final int pages;

        Building(String letter, String address, String url, int pages) {
            this.letter = letter;
            this.address = address;
            this.url = url;
            this.pages = pages;
        }
    }

    private static final Building[] TABLE = {
        new Building("А", "Лоцманская ул., 3",
            "https://isu.smtu.ru/doc_tree_file/9340022def0eb135cfe16946c8e678a262a93545ddb0ddbfa3cfcabeec27ce4a/", 5),
        new Building("Б", "Лоцманская ул., 10-14",
            "https://isu.smtu.ru/doc_tree_file/e556fc798afbfcab2900f2966edc78ad69a1d7660e1f00c381cdd8d7a73b4551/", 6),
        new Building("У", "Ленинский пр., 101",
            "https://isu.smtu.ru/doc_tree_file/50c1eeb37e952c41b6737c1acccdc1c1b39cea4a604e750df94649707029de15/", 5),
        new Building("Г", "Кронверкский пр., 5", null, 0),
        new Building("М", "Псковская ул., 23", null, 0),
    };

    /** "509 Корпус Г" / "каф.ИЯ Корпус У" -> building entry, or null. */
    static Building ofRoom(String room) {
        if (room == null) return null;
        int i = room.indexOf("Корпус");
        if (i < 0) return null;
        for (int j = i + 6; j < room.length(); j++) {
            char c = room.charAt(j);
            if (c >= 'А' && c <= 'Я') {
                String letter = String.valueOf(c);
                for (Building b : TABLE) if (b.letter.equals(letter)) return b;
                return null;
            }
        }
        return null;
    }

    /** Floor from the leading room number: "509 Корпус Г" -> 5; "каф.ИЯ…" -> -1. */
    static int floorOf(String room) {
        for (int i = 0; i < room.length(); i++) {
            char c = room.charAt(i);
            if (c >= '0' && c <= '9') return c - '0';
            if (c == ' ') break;
        }
        return -1;
    }

    /** Short line for the lesson dialog, e.g. "Корпус У, 5 этаж". */
    static String label(Building b, String room) {
        int fl = floorOf(room);
        return "Корпус " + b.letter + (fl > 0 ? ", " + fl + " этаж" : "");
    }

    interface Ready { void done(File pdf, String error); }

    /** Open the floor-plan dialog for the lesson's building and room. */
    static void show(final Activity a, final Building b, final String room) {
        if (b.url == null) {
            new AlertDialog.Builder(a)
                    .setTitle("Корпус " + b.letter)
                    .setMessage("Адрес: " + b.address
                            + "\n\nПоэтажные планы этого корпуса не опубликованы.")
                    .setPositiveButton("Закрыть", null)
                    .show();
            return;
        }
        final int floor = floorOf(room);
        final String title = label(b, room)
                + (room.matches("\\d+.*") ? " — ауд. " + room.split(" ")[0] : "");
        final AlertDialog loading = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage("Загрузка плана корпуса…")
                .setNegativeButton("Закрыть", null)
                .create();
        loading.show();
        ensurePdf(a, b, (pdf, err) -> {
            loading.dismiss();
            if (err != null) {
                Toast.makeText(a, "Не удалось скачать план: " + err, Toast.LENGTH_LONG).show();
                return;
            }
            render(a, pdf, floor, title);
        });
    }

    /** Cached PDF or a one-time download (writes map_<letter>.pdf via .part). */
    private static void ensurePdf(final Context ctx, final Building b, final Ready cb) {
        final File f = new File(ctx.getFilesDir(), "map_" + b.letter + ".pdf");
        if (f.exists() && f.length() > 0) {
            cb.done(f, null);
            return;
        }
        new Thread(() -> {
            String err = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(b.url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(60000);
                InputStream in = c.getInputStream();
                File tmp = new File(ctx.getFilesDir(), "map_" + b.letter + ".part");
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                out.close();
                in.close();
                if (!tmp.renameTo(f)) throw new IllegalStateException("не удалось сохранить файл");
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String e2 = err;
            new Handler(Looper.getMainLooper()).post(() -> cb.done(f, e2));
        }).start();
    }

    /** Render the floor page off-thread and show it in a pinch-zoom dialog. */
    private static void render(final Activity a, final File pdf, final int floor, final String title) {
        final ZoomView zv = new ZoomView(a);
        LinearLayout box = new LinearLayout(a);
        box.setBackgroundColor(0xFF444444);
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.6f);
        zv.setLayoutParams(new LinearLayout.LayoutParams(Math.min(w, h * 4 / 3), h));
        box.addView(zv);
        final AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();
        d.show();
        new Thread(() -> {
            Bitmap bmp = null;
            String err = null;
            try (ParcelFileDescriptor fd =
                         ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer r = new PdfRenderer(fd)) {
                int page = Math.max(0, Math.min(r.getPageCount() - 1, floor - 1));
                PdfRenderer.Page p = r.openPage(page);
                float k = Math.min(4f, 2560f / Math.max(p.getWidth(), p.getHeight()));
                bmp = Bitmap.createBitmap(Math.round(p.getWidth() * k),
                        Math.round(p.getHeight() * k), Bitmap.Config.RGB_565);
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final Bitmap b2 = bmp;
            final String e2 = err;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (b2 != null) {
                    zv.setPage(b2);
                } else {
                    d.dismiss();
                    pdf.delete();           // corrupt download: retry next time
                    Toast.makeText(a, "План повреждён — скачайте ещё раз", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /** Bitmap with pinch-zoom and drag-pan; centers the fit-to-view image. */
    static final class ZoomView extends View {
        private Bitmap page;
        private float scale = 1f, fit = 1f, tx, ty;
        private final ScaleGestureDetector pinch;
        private final GestureDetector pan;

        ZoomView(Context c) {
            super(c);
            pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    float ns = Math.max(fit * 0.8f, Math.min(scale * d.getScaleFactor(), fit * 8f));
                    float f = ns / scale;
                    tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                    ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                    scale = ns;
                    invalidate();
                    return true;
                }
            });
            pan = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }

                @Override public boolean onScroll(MotionEvent a, MotionEvent e, float dx, float dy) {
                    tx -= dx;
                    ty -= dy;
                    clamp();
                    invalidate();
                    return true;
                }
            });
        }

        void setPage(Bitmap b) {
            page = b;
            fitToView();
            invalidate();
        }

        private void fitToView() {
            if (page == null || getWidth() == 0) return;
            fit = Math.min(getWidth() / (float) page.getWidth(),
                    getHeight() / (float) page.getHeight());
            scale = fit;
            tx = (getWidth() - page.getWidth() * scale) / 2f;
            ty = (getHeight() - page.getHeight() * scale) / 2f;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { fitToView(); }

        @Override protected void onDraw(Canvas c) {
            if (page == null) {
                c.drawColor(Color.DKGRAY);
                return;
            }
            c.translate(tx, ty);
            c.scale(scale, scale);
            c.drawBitmap(page, 0, 0, null);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            pinch.onTouchEvent(e);
            if (!pinch.isInProgress()) pan.onTouchEvent(e);
            return true;
        }

        private void clamp() {
            if (page == null) return;
            float w = page.getWidth() * scale, h = page.getHeight() * scale;
            tx = w <= getWidth() ? (getWidth() - w) / 2f
                    : Math.max(getWidth() - w, Math.min(0, tx));
            ty = h <= getHeight() ? (getHeight() - h) / 2f
                    : Math.max(getHeight() - h, Math.min(0, ty));
        }
    }
}
