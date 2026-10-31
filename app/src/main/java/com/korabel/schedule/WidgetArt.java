package com.korabel.schedule;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;

import com.korabel.schedule.Smtu.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas-drawn widget pieces: the 60-minute timeline bar and vertical text
 * rendering for the name widget. RemoteViews XML cannot draw dynamic segments
 * or measure text, so these are rendered to bitmaps and pushed through
 * setImageViewBitmap.
 */
public final class WidgetArt {

    public static final int WINDOW_MIN = 60;   // timeline span: the coming hour
    public static final int CELL_MIN = 10;     // alternating cell length

    /** One colored stretch of the timeline: [from, to) minutes of day. */
    public static final class Period {
        public final int from, to;
        public final boolean lesson;

        Period(int from, int to, boolean lesson) {
            this.from = from;
            this.to = to;
            this.lesson = lesson;
        }
    }

    private WidgetArt() { }

    // ---------------------------------------------------------------- geometry

    /** Orientation from the option bundle: horizontal unless clearly taller. */
    public static boolean isHorizontal(android.os.Bundle o) {
        return o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                >= o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
    }

    /** Widget width in dp per the option bundle, or defW when unavailable. */
    public static int widthDp(android.os.Bundle o, int defW) {
        int w = isHorizontal(o)
                ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        return w > 0 ? w : defW;
    }

    /** Widget height in dp per the option bundle, or defH when unavailable. */
    public static int heightDp(android.os.Bundle o, int defH) {
        int h = isHorizontal(o)
                ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        return h > 0 ? h : defH;
    }

    // ---------------------------------------------------------------- periods

    /**
     * Periods covering [now, now+WINDOW]: the ongoing lesson or the current
     * break first, then upcoming lesson+break pairs until the hour is full.
     * Cell phase stays anchored to real period starts (lesson start, gap
     * start) so the color rhythm holds steady as the bar scrolls left.
     */
    public static List<Period> periods(Now.State st) {
        List<Period> out = new ArrayList<>();
        if (st.kind == Now.ONGOING && st.current != null) {
            out.add(new Period(Now.startMin(st.current), Now.endMin(st.current), true));
        } else if ((st.kind == Now.BREAK || st.kind == Now.BEFORE) && st.next != null) {
            int ns = Now.startMin(st.next);
            int from = Math.min(st.nowMin, ns - st.gapMin);
            out.add(new Period(from, ns, false));
        } else {
            return out;                       // AFTER / NONE: the hour is empty
        }
        int end = st.nowMin + WINDOW_MIN;
        int lastEnd = out.get(out.size() - 1).to;
        for (Slot s : st.today) {
            int a = Now.startMin(s), b = Now.endMin(s);
            if (b <= a || a < lastEnd) continue;   // past, ongoing, or overlapping
            if (a >= end) {
                if (lastEnd < end) out.add(new Period(lastEnd, end, false));
                lastEnd = end;
                break;                   // window already full
            }
            if (a > lastEnd) out.add(new Period(lastEnd, a, false));
            out.add(new Period(a, Math.min(b, end), true));
            lastEnd = b;
            if (lastEnd >= end) break;
        }
        if (lastEnd < end) {
            out.add(new Period(lastEnd, end, false));
        }
        return out;
    }

    // ---------------------------------------------------------------- timeline

    /**
     * The timeline bar: a "now" triangle at the left edge, then the coming
     * hour as 10-minute cells that scroll right-to-left as time passes.
     * vertical=true rotates it so the marker sits at the bottom and the
     * future runs upward.
     */
    public static Bitmap timeline(Context ctx, Now.State st, int wPx, int hPx, boolean vertical) {
        if (wPx <= 0 || hPx <= 0) return null;
        if (vertical) {
            Bitmap h = timelineH(ctx, st, hPx, wPx, 14);
            Matrix m = new Matrix();
            m.postRotate(-90);                // left edge -> bottom, future -> up
            Bitmap v = Bitmap.createBitmap(h, 0, 0, h.getWidth(), h.getHeight(), m, false);
            h.recycle();
            return v;
        }
        return timelineH(ctx, st, wPx, hPx, 14);
    }

    private static Bitmap timelineH(Context ctx, Now.State st, int w, int h, int markerDp) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float d = ctx.getResources().getDisplayMetrics().density;

        int mw = Math.round(Math.min(h, markerDp * d));   // marker zone, capped for thick bars
        float barX = mw, barW = w - mw;
        float r = h / 2f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);

        RectF track = new RectF(barX, 0, w, h);
        p.setColor(ctx.getColor(R.color.w_ring_track));
        c.drawRoundRect(track, r, r, p);

        List<Period> ps = periods(st);
        if (!ps.isEmpty()) {
            int save = c.save();
            Path clip = new Path();
            clip.addRoundRect(track, r, r, Path.Direction.CW);
            c.clipPath(clip);
            float pxMin = barW / (float) WINDOW_MIN;
            int winEnd = st.nowMin + WINDOW_MIN;
            for (Period per : ps) {
                int col1 = ctx.getColor(per.lesson ? R.color.w_ring_lesson : R.color.w_ring_break);
                int col2 = ctx.getColor(per.lesson ? R.color.w_lesson2 : R.color.w_break2);
                for (int s = per.from; s < per.to && s < winEnd; s += CELL_MIN) {
                    int cs = Math.max(s, st.nowMin);
                    int ce = Math.min(Math.min(s + CELL_MIN, per.to), winEnd);
                    if (ce <= cs) continue;
                    p.setColor(((s - per.from) / CELL_MIN) % 2 == 0 ? col1 : col2);
                    c.drawRect(barX + (cs - st.nowMin) * pxMin, 0,
                            barX + (ce - st.nowMin) * pxMin, h, p);
                }
            }
            c.restoreToCount(save);
        }

        // "now" triangle pointing at the first upcoming minute
        float t = Math.min(h * 0.4f, mw * 0.6f);
        float cy = h / 2f;
        Path tri = new Path();
        tri.moveTo(barX - t * 1.35f, cy - t);
        tri.lineTo(barX - t * 1.35f, cy + t);
        tri.lineTo(barX, cy);
        tri.close();
        p.setColor(markerColor(ctx, st));
        c.drawPath(tri, p);
        return bmp;
    }

    private static int markerColor(Context ctx, Now.State st) {
        int res = st.kind == Now.ONGOING ? R.color.w_ring_lesson
                : (st.kind == Now.BREAK || st.kind == Now.BEFORE) ? R.color.w_ring_break
                : R.color.w_sub;
        return ctx.getColor(res);
    }

    // ---------------------------------------------------------------- ring

    /** Ring progress 0..100: drains while a lesson runs, fills during a break. */
    public static int ringPct(Now.State st) {
        if (st.kind == Now.ONGOING) {
            int total = Now.endMin(st.current) - Now.startMin(st.current);
            return pct(st.remainMin, total);
        }
        if (st.kind == Now.BREAK || st.kind == Now.BEFORE) {
            // Fill only the countdown window the label actually shows, so the
            // ring stays empty (not half-frozen) while the pair is far away
            // and then fills continuously once "N мин до пары" starts ticking.
            int win = Math.min(st.gapMin, 90);
            return pct(win - st.remainMin, win);
        }
        return 0;
    }

    /** Ring accent color for the state, or 0 when no ring is shown. */
    public static int ringColorRes(Now.State st) {
        return st.kind == Now.ONGOING ? R.color.w_ring_lesson
                : (st.kind == Now.BREAK || st.kind == Now.BEFORE) ? R.color.w_ring_break : 0;
    }

    /** Center label of the ring: {main, sub} — same rules as the 1x1 timer. */
    public static String[] ringText(Now.State st) {
        if (st.kind == Now.ONGOING) return new String[]{String.valueOf(st.remainMin), "мин"};
        if (st.kind == Now.BREAK || st.kind == Now.BEFORE) {
            if (st.remainMin > 90) return new String[]{st.next.time.substring(0, 5), "начало"};
            return new String[]{String.valueOf(st.remainMin), "до пары"};
        }
        if (st.kind == Now.AFTER && st.next != null) {
            return new String[]{st.next.time.substring(0, 5),
                    Now.isTomorrow(st) ? "завтра" : Now.weekdayName(st.nextDay)};
        }
        return new String[]{"—", st.gid == null ? "нет группы" : "нет пар"};
    }

    // ---------------------------------------------------------------- vertical text

    /** One vertical line: text, its size in sp, boldness, color, column cap. */
    public static final class VLine {
        public final CharSequence text;
        public final float sizeSp;
        public final boolean bold;
        public final int colorRes;
        public final int maxColumns;

        public VLine(CharSequence text, float sizeSp, boolean bold, int colorRes, int maxColumns) {
            this.text = text;
            this.sizeSp = sizeSp;
            this.bold = bold;
            this.colorRes = colorRes;
            this.maxColumns = maxColumns;
        }
    }

    /**
     * Vertical text for the tall name card: upright letters stacked
     * top-to-bottom, columns flowing right-to-left, so the first line
     * occupies the rightmost column group. Text that overflows a column
     * continues one column to the left; a line that exceeds its column cap
     * is ellipsized. When the lines together are wider than the card, tail
     * lines give up columns first (subject keeps priority) and are dropped
     * last.
     */
    public static Bitmap vtext(Context ctx, int wPx, int hPx, List<VLine> lines) {
        if (wPx <= 0 || hPx <= 0 || lines.isEmpty()) return null;
        float sd = ctx.getResources().getDisplayMetrics().scaledDensity;
        float d = ctx.getResources().getDisplayMetrics().density;
        int n = lines.size();

        TextPaint[] ps = new TextPaint[n];
        float[] step = new float[n], colW = new float[n];
        int[] perCol = new int[n], cols = new int[n];
        for (int i = 0; i < n; i++) {
            VLine l = lines.get(i);
            TextPaint p = text(l.sizeSp * sd, l.bold, ctx.getColor(l.colorRes));
            ps[i] = p;
            step[i] = p.getTextSize() * 1.15f;              // room for descenders
            float wide = 0;
            for (int k = 0; k < l.text.length(); k++) {     // widest glyph
                char ch = l.text.charAt(k);
                if (ch == ' ') continue;
                wide = Math.max(wide, p.measureText(String.valueOf(ch)));
            }
            colW[i] = Math.max(wide, p.getTextSize() * 0.6f) + 2 * d;
            perCol[i] = Math.max(1, (int) (hPx / step[i]));
            cols[i] = Math.min(l.maxColumns,
                    (l.text.length() + perCol[i] - 1) / perCol[i]);
        }

        float gap = 3 * d;
        int m = n;                                          // active lines
        while (m > 1) {
            float w = (m - 1) * gap;
            for (int i = 0; i < m; i++) w += cols[i] * colW[i];
            if (w <= wPx) break;
            int i = m - 1;                                  // tail line yields first
            while (i > 0 && cols[i] <= 1) i--;
            if (i > 0) cols[i]--;
            else m--;                                       // all tails at 1: drop one
        }
        while (cols[0] > 1) {                               // last resort: subject
            float w = (m - 1) * gap;
            for (int i = 0; i < m; i++) w += cols[i] * colW[i];
            if (w <= wPx) break;
            cols[0]--;
        }

        Bitmap bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float xRight = wPx;
        for (int li = 0; li < m; li++) {
            TextPaint p = ps[li];
            CharSequence t = lines.get(li).text;
            int pc = perCol[li];
            int cap = cols[li] * pc;
            int len = t.length();
            int visible = len > cap ? cap - 1 : len;        // room for the ellipsis
            for (int j = 0; j < cols[li]; j++) {            // column 0 = rightmost
                float cx = xRight - (j + 1) * colW[li];
                int from = j * pc;
                int to = Math.min(from + pc, visible);
                for (int k = from; k < to; k++) {
                    char ch = t.charAt(k);
                    if (ch == ' ') continue;                // spaces consume a slot
                    drawVChar(c, p, ch, cx, colW[li], (k - from) * step[li], step[li]);
                }
                if (len > cap && j == cols[li] - 1)         // … in the freed slot
                    drawVChar(c, p, '\u2026', cx, colW[li], (pc - 1) * step[li], step[li]);
            }
            xRight -= cols[li] * colW[li] + gap;
        }
        return bmp;
    }

    /** One upright glyph centered in its column (width cw) and its step-tall slot. */
    private static void drawVChar(Canvas c, TextPaint p, char ch,
                                  float cx, float cw, float slotTop, float step) {
        String s = String.valueOf(ch);
        float x = cx + (cw - p.measureText(s)) / 2f;
        float y = slotTop + (step - textH(p)) / 2f - p.ascent();
        c.drawText(s, x, y, p);
    }

    // ---------------------------------------------------------------- helpers

    private static TextPaint text(float size, boolean bold, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        p.setColor(color);
        return p;
    }

    private static float textH(TextPaint p) {
        return -p.ascent() + p.descent();
    }

    private static int pct(int part, int whole) {
        if (whole <= 0) return 0;
        return Math.max(0, Math.min(100, part * 100 / whole));
    }
}
