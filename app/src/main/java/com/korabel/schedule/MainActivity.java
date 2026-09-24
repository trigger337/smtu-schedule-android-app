package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.OverScroller;
import android.widget.TextView;
import android.widget.Toast;

import com.korabel.schedule.Smtu.Callback;
import com.korabel.schedule.Smtu.Group;
import com.korabel.schedule.Smtu.Slot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Minimal native СПбГМТУ schedule app. Data comes from www.smtu.ru only
 * (the authoritative source), parsed from the server-rendered schedule pages.
 * One Activity, programmatic views, no external libraries.
 *
 * Header: group name, date range, upper/lower week badge, ‹ › navigation.
 * Week mode shows Mon–Sun of the selected week; day mode a single day with a
 * weekday quick-switch strip. Lessons match a day via their exact occurrence
 * dates (the title attribute on the site). Tapping a lesson opens details;
 * from there: all occurrences of the subject, or the teacher's full schedule.
 */
public final class MainActivity extends Activity {

    private static final String PREFS = "sched";
    private static final String PREF_GID = "groupId";
    private static final String PREF_GNAME = "groupName";
    private static final String PREF_DAY = "dayMode";
    private static final Locale RU = new Locale("ru");

    // views
    private TextView tvGroup, tvRange, tvWeek, tvEmpty;
    private LinearLayout dayStrip;
    private ListView list;                 // primary page; the pager swaps in the alt one
    private DayPager pager;
    private Button btnMode;

    // the backdrop band moves once a minute — re-draw visible rows in place
    private final Handler ticker = new Handler();
    private final Runnable tickRun = new Runnable() {
        @Override public void run() { refreshFills(); scheduleTick(); }
    };

    // state
    private String groupId, groupName;
    private List<Slot> slots = new ArrayList<>();
    private boolean dayMode = false;
    private Calendar anchor = Dates.monday(Dates.today());      // Monday of shown week
    private int dayOffset = (Dates.today().get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0..Sun=6

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        groupId = p.getString(PREF_GID, null);
        groupName = p.getString(PREF_GNAME, groupId == null ? null : groupId);
        dayMode = p.getBoolean(PREF_DAY, false);
        if (savedInstanceState != null) {
            dayMode = savedInstanceState.getBoolean("day");
            anchor.setTimeInMillis(savedInstanceState.getLong("anchor"));
            dayOffset = savedInstanceState.getInt("off");
        }
        if (groupId == null) showGroupPicker(); else load();
        UpdateChecker.onLaunch(this);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("day", dayMode);
        out.putLong("anchor", anchor.getTimeInMillis());
        out.putInt("off", dayOffset);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshFills();
        scheduleTick();
    }

    @Override protected void onPause() {
        super.onPause();
        ticker.removeCallbacks(tickRun);
    }

    // ------------------------------------------------------------------- data

    private void load() {
        slots = Smtu.loadCacheSync(this, false, groupId);
        tvGroup.setText(groupName);
        render();          // instant from cache...
        refresh();         // ...then silently update from the network
        WidgetUpdater.poke(this);
    }

    private void refresh() {
        if (groupId == null) { showGroupPicker(); return; }
        Smtu.schedule(this, false, groupId, (result, error) -> {
            slots = result;
            render();
            if (error != null) toast(error);
            WidgetUpdater.poke(this);
        });
    }

    // ------------------------------------------------------------------- ui

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(8), dp(10), dp(8), dp(8));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        Button prev = navButton("‹");
        prev.setOnClickListener(v -> shift(-1));
        Button next = navButton("›");
        next.setOnClickListener(v -> shift(1));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(4), 0, dp(4), 0);

        tvGroup = new TextView(this);
        tvGroup.setTextSize(20);
        tvGroup.setTypeface(Typeface.DEFAULT_BOLD);
        tvGroup.setTextColor(getColor(R.color.text_primary));
        title.addView(tvGroup);

        tvRange = new TextView(this);
        tvRange.setTextSize(13);
        tvRange.setTextColor(getColor(R.color.text_secondary));
        title.addView(tvRange);
        title.setOnClickListener(v -> showGroupPicker()); // tap title = change group

        row1.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row1.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        row1.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        btnMode = new Button(this);
        btnMode.setTextSize(13);
        btnMode.setAllCaps(false);
        btnMode.setPadding(dp(12), 0, dp(12), 0);
        btnMode.setOnClickListener(v -> {
            dayMode = !dayMode;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_DAY, dayMode).apply();
            render();
        });
        row2.addView(btnMode, new LinearLayout.LayoutParams(-2, dp(36)));

        tvWeek = new TextView(this);
        tvWeek.setTextSize(12);
        tvWeek.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeek.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
        wp.leftMargin = dp(8);
        row2.addView(tvWeek, wp);

        Button refresh = new Button(this);
        refresh.setText("⟳");
        refresh.setTextSize(16);
        refresh.setPadding(0, 0, 0, 0);
        refresh.setOnClickListener(v -> refresh());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(36), dp(36));
        rp.leftMargin = dp(8);
        row2.addView(refresh, rp);

        View gap = new View(this);
        row2.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));

        Button info = new Button(this);
        info.setText("ⓘ");
        info.setTextSize(16);
        info.setAllCaps(false);
        info.setPadding(0, 0, 0, 0);
        info.setOnClickListener(v -> showInfo());
        row2.addView(info, new LinearLayout.LayoutParams(dp(36), dp(36)));

        dayStrip = new LinearLayout(this);
        dayStrip.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(dayStrip, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        tvEmpty = new TextView(this);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setTextColor(getColor(R.color.text_hint));
        tvEmpty.setTextSize(15);
        tvEmpty.setPadding(dp(24), dp(24), dp(24), dp(24));
        body.addView(tvEmpty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(8), 0, dp(8), dp(16));
        list.setClipToPadding(false);
        pager = new DayPager(this);
        pager.addView(list, new FrameLayout.LayoutParams(-1, -1));
        body.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(tvEmpty);

        return root;
    }

    private Button navButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(24);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    // -------------------------------------------------------------- rendering

    private void render() {
        pager.reset();
        renderPage();
        updateChrome();
    }

    /** Rebuild the visible list only — the pager calls this on its own swaps. */
    private void renderPage() {
        if (dayMode) {
            list.setAdapter(new RowsAdapter(rowsFor(Dates.plus(anchor, dayOffset))));
        } else {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < 7; i++) rows.addAll(rowsFor(Dates.plus(anchor, i)));
            list.setAdapter(new RowsAdapter(rows));
            for (int i = 0; i < rows.size(); i++)      // open the week on today
                if (rows.get(i).today && rows.get(i).slot == null) { list.setSelection(i); break; }
        }
    }

    /** Header + lesson rows of one day, sharing that day's sorted slot list. */
    private List<Row> rowsFor(Calendar day) {
        List<Slot> of = Now.slotsOf(this, slots, day);
        Collections.sort(of, (a, b) -> Now.startMin(a) - Now.startMin(b));
        List<Row> rows = new ArrayList<>();
        boolean today = Dates.isToday(day);
        rows.add(new Row(day, today, null, of, -1));
        for (int i = 0; i < of.size(); i++) rows.add(new Row(day, today, of.get(i), of, i));
        return rows;
    }

    private void updateChrome() {
        btnMode.setText(dayMode ? "▶ Неделя" : "▶ День");
        buildDayStrip();
        tvEmpty.setText(slots.isEmpty()
                ? "Нет данных.\nНажмите ⟳ при подключении к сети."
                : "Занятий нет.");

        tvRange.setText(rangeText());
        boolean upper = Smtu.isUpperWeek(this, dayMode ? Dates.plus(anchor, dayOffset) : anchor);
        tvWeek.setText(upper ? "ВЕРХНЯЯ НЕДЕЛЯ" : "НИЖНЯЯ НЕДЕЛЯ");
        tvWeek.setBackgroundColor(upper ? getColor(R.color.week_upper) : getColor(R.color.week_lower));
        tvWeek.setTextColor(Color.WHITE);
    }

    private String rangeText() {
        SimpleDateFormat df = new SimpleDateFormat("d MMMM", RU);
        if (dayMode)
            return new SimpleDateFormat("d MMMM, EEEE", Dates.RU).format(Dates.plus(anchor, dayOffset).getTime());
        String a = df.format(anchor.getTime());
        String b = df.format(Dates.plus(anchor, 6).getTime());
        return sameMonth(anchor, Dates.plus(anchor, 6)) ? a.split(" ")[0] + " – " + b : a + " – " + b;
    }

    private static boolean sameMonth(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.MONTH) == b.get(Calendar.MONTH);
    }

    // ------------------------------------------------------------- navigation

    private void shift(int dir) {
        if (dayMode) stepDay(dir);
        else anchor.add(Calendar.DAY_OF_YEAR, 7 * dir);
        render(); // full semester is already loaded: no refetch needed
    }

    private void stepDay(int dir) {
        dayOffset += dir;
        while (dayOffset > 6) { dayOffset -= 7; anchor.add(Calendar.DAY_OF_YEAR, 7); }
        while (dayOffset < 0) { dayOffset += 7; anchor.add(Calendar.DAY_OF_YEAR, -7); }
    }

    private void buildDayStrip() {
        dayStrip.setVisibility(dayMode ? View.VISIBLE : View.GONE);
        dayStrip.removeAllViews();
        if (!dayMode) return;
        String[] names = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView b = new TextView(this);
            b.setText(names[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(13);
            boolean sel = i == dayOffset;
            b.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setTextColor(sel ? getColor(R.color.day_sel) : getColor(R.color.text_meta));
            b.setBackgroundColor(sel ? getColor(R.color.chip_sel) : Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            b.setOnClickListener(v -> { dayOffset = idx; render(); });
            dayStrip.addView(b, lp);
        }
    }

    // ----------------------------------------------------------- list adapter

    /** One list row: a day header (slot == null) or a lesson of that day. */
    private static final class Row {
        final Calendar day; final boolean today; final Slot slot;
        final List<Slot> daySlots; final int idx;   // slot's index in daySlots, -1 for the header
        Row(Calendar d, boolean t, Slot s, List<Slot> of, int i) {
            day = d; today = t; slot = s; daySlots = of; idx = i;
        }
    }

    private final class RowsAdapter extends BaseAdapter {
        private final List<Row> rows;
        RowsAdapter(List<Row> rows) { this.rows = rows; }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            Row r = rows.get(i);
            return r.slot == null ? headerView(r, convert) : lessonView(r, convert);
        }

        private View headerView(Row r, View convert) {
            TextView tv = (convert instanceof TextView) ? (TextView) convert : new TextView(MainActivity.this);
            String label = new SimpleDateFormat("EEEE, d MMMM", RU).format(r.day.getTime());
            label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
            tv.setText(label + (r.today ? "  •  сегодня" : ""));
            tv.setTextSize(13);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextColor(getColor(R.color.text_muted));
            tv.setBackgroundColor(r.today ? getColor(R.color.today_bg) : getColor(R.color.header_bg));
            tv.setPadding(dp(12), dp(10), dp(12), dp(10));
            return tv;
        }

        private View lessonView(final Row r, View convert) {
            final Slot s = r.slot;
            LinearLayout row = (convert instanceof LinearLayout) ? (LinearLayout) convert : null;
            if (row == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setBackground(new FillDrawable());   // translucent backdrop, see applyFill

                TextView time = new TextView(MainActivity.this);
                time.setId(1);
                time.setTextSize(13);
                time.setTextColor(getColor(R.color.time));
                time.setTypeface(Typeface.DEFAULT_BOLD);
                time.setGravity(Gravity.CENTER);
                time.setLineSpacing(0, 0.85f);
                row.addView(time, new LinearLayout.LayoutParams(dp(52), -2));

                LinearLayout mid = new LinearLayout(MainActivity.this);
                mid.setId(2);
                mid.setOrientation(LinearLayout.VERTICAL);
                row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView subj = new TextView(MainActivity.this);
                subj.setId(3);
                subj.setTextSize(15);
                subj.setTextColor(getColor(R.color.text_primary));
                mid.addView(subj);

                TextView meta = new TextView(MainActivity.this);
                meta.setId(4);
                meta.setTextSize(12);
                meta.setTextColor(getColor(R.color.text_meta));
                mid.addView(meta);

                TextView room = new TextView(MainActivity.this);
                room.setId(5);
                room.setTextSize(12);
                room.setTextColor(getColor(R.color.text_muted));
                room.setGravity(Gravity.END);
                row.addView(room, new LinearLayout.LayoutParams(dp(64), -2));
            }
            TextView time = row.findViewById(1), subj = row.findViewById(3),
                     meta = row.findViewById(4), room = row.findViewById(5);
            // time as three compact centered lines: start / – / end
            String[] t = s.time.split(" - ");
            if (t.length == 2) {
                SpannableString ts = new SpannableString(t[0] + "\n–\n" + t[1]);
                ts.setSpan(new RelativeSizeSpan(0.7f), t[0].length() + 1, t[0].length() + 2, 0);
                time.setText(ts, TextView.BufferType.SPANNABLE);
            } else {
                time.setText(s.time);
            }
            subj.setText(s.subject);
            meta.setText(s.teacher.isEmpty() ? s.type : s.type + " · " + s.teacher);
            // room as two lines: number/label over building
            int sp = s.room.indexOf(' ');
            room.setText(sp > 0 ? s.room.substring(0, sp) + "\n" + s.room.substring(sp + 1) : s.room);
            applyFill(r, row);
            row.setOnClickListener(v -> showLesson(s));
            return row;
        }
    }

    /** Translucent backdrop of one lesson row: gray above, time-left band below. */
    private void applyFill(Row r, View row) {
        Now.Band b = Now.band(r.day, r.daySlots, r.idx);
        ((FillDrawable) row.getBackground())
                .set(getColor(R.color.row), getColor(R.color.fill_past), getColor(b.colorRes), b.frac);
    }

    /** Minute tick: redraw the backdrops of the visible rows, without rescrolling. */
    private void refreshFills() {
        if (!(list.getAdapter() instanceof RowsAdapter)) return;
        RowsAdapter a = (RowsAdapter) list.getAdapter();
        int first = list.getFirstVisiblePosition();
        for (int p = first; p <= list.getLastVisiblePosition() && p < a.rows.size(); p++) {
            Row r = a.rows.get(p);
            View v = list.getChildAt(p - first);
            if (r.slot != null && v instanceof LinearLayout) applyFill(r, v);
        }
    }

    private void scheduleTick() {
        ticker.removeCallbacks(tickRun);
        ticker.postDelayed(tickRun, 61_000 - System.currentTimeMillis() % 60_000);
    }

    // ----------------------------------------------------------------- pager

    /**
     * Hand-rolled horizontal pager for day mode (the project deliberately
     * has no dependencies, so no ViewPager): a finger-following drag between
     * two ListViews with a fling that can chain up to three one-day slides.
     * In week mode it never intercepts and the primary list fills it alone.
     */
    private final class DayPager extends FrameLayout {

        private ListView alt;              // secondary page, created on first drag
        private int side;                  // 0 none, +1 alt to the right (next day), -1 to the left
        private float ofs;                 // primary page translation, <0 sliding toward next day
        private final OverScroller sc;
        private VelocityTracker vt;
        private float downX, downY, baseOfs;
        private boolean horiz, settling;
        private int settleDir, settleLeft; // chained settle: direction and days still to go
        private final int slop;

        DayPager(Context c) {
            super(c);
            sc = new OverScroller(c);
            slop = ViewConfiguration.get(c).getScaledPagingTouchSlop();
        }

        private int w() { return Math.max(1, getWidth()); }

        void reset() {
            settling = false;
            sc.abortAnimation();
            ofs = 0;
            side = 0;
            list.setTranslationX(0);
            if (alt != null) { alt.setTranslationX(0); alt.setVisibility(GONE); }
        }

        private void bindAlt(int dir) {
            if (alt == null) {
                alt = new ListView(MainActivity.this);
                alt.setDivider(null);
                alt.setPadding(dp(8), 0, dp(8), dp(16));
                alt.setClipToPadding(false);
                addView(alt, 1, new FrameLayout.LayoutParams(-1, -1));
            }
            alt.setVisibility(VISIBLE);
            alt.setAdapter(new RowsAdapter(rowsFor(Dates.plus(anchor, dayOffset + dir))));
            side = dir;
            applyOfs();
        }

        private void applyOfs() {
            list.setTranslationX(ofs);
            if (alt != null && side != 0) alt.setTranslationX(ofs + side * w());
        }

        private void trackStart(MotionEvent e) {
            downX = e.getX();
            downY = e.getY();
            baseOfs = 0;
            horiz = false;
            if (vt != null) vt.recycle();
            vt = VelocityTracker.obtain();
            vt.addMovement(e);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!dayMode) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    trackStart(e);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    if (!horiz) {
                        float dx = e.getX() - downX, dy = e.getY() - downY;
                        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                            if (!sc.isFinished()) {          // grab a settle already running
                                ofs = sc.getCurrX();
                                sc.abortAnimation();
                                settling = false;
                            }
                            baseOfs = ofs;
                            downX = e.getX();               // the drag continues from here
                            horiz = true;
                        }
                    }
                    if (horiz) return true;
                    break;
                default:
                    if (vt != null) vt.addMovement(e);
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    trackStart(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    if (horiz) {
                        ofs = Math.max(-w(), Math.min(w(), baseOfs + e.getX() - downX));
                        if (Math.abs(ofs) > slop) {
                            int d = ofs < 0 ? 1 : -1;       // which day alt should show
                            if (side != d) bindAlt(d);
                        }
                        applyOfs();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (vt != null) vt.addMovement(e);
                    endDrag(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private void endDrag(boolean cancel) {
            float v = 0;
            if (vt != null) {
                if (!cancel && horiz) {
                    vt.computeCurrentVelocity(1000, 15000);
                    v = vt.getXVelocity();
                }
                vt.recycle();
                vt = null;
            }
            if (!horiz) return;                             // never dragged: nothing to settle
            horiz = false;
            int d = 0;
            if (!cancel) {
                float proj = ofs + v * 0.22f;               // where the fling meant to land
                d = Math.max(-3, Math.min(3, Math.round(-proj / w())));
                if (d != 0 && side != 0 && side != (d > 0 ? 1 : -1)
                        && Math.abs(ofs) > w() / 10) d = 0; // never teleport a visible page
            }
            settle(d);
        }

        private void settle(int d) {
            settling = true;
            settleLeft = d;
            if (d != 0) {
                settleDir = d > 0 ? 1 : -1;
                if (side != settleDir) bindAlt(settleDir);
            }
            leg();
        }

        private void leg() {
            int target = settleLeft == 0 ? 0 : -settleDir * w();
            sc.startScroll(Math.round(ofs), 0, target - Math.round(ofs), 260);
            postInvalidateOnAnimation();
        }

        @Override public void computeScroll() {
            if (sc.computeScrollOffset()) {
                ofs = sc.getCurrX();
                applyOfs();
                postInvalidateOnAnimation();
            } else if (settling) {
                legDone();
            }
        }

        private void legDone() {
            if (settleLeft == 0) {                           // slid back home
                settling = false;
                side = 0;
                ofs = 0;
                applyOfs();
                if (alt != null) alt.setVisibility(GONE);
                return;
            }
            ListView t = list;                               // promote the arriving page
            list = alt;
            alt = t;
            stepDay(settleDir);
            settleLeft -= settleDir;
            ofs = 0;
            list.setTranslationX(0);
            if (settleLeft == 0) {
                settling = false;
                side = 0;
                alt.setTranslationX(0);
                alt.setVisibility(GONE);
                updateChrome();
            } else {
                bindAlt(settleDir);
                leg();
            }
        }
    }

    // ---------------------------------------------------------------- dialogs

    private void showLesson(Slot s) {
        StringBuilder m = new StringBuilder();
        m.append(s.day).append(", ").append(s.time.replace(" - ", " – ")).append('\n')
         .append(s.type);
        if (!s.room.isEmpty()) m.append(" · ").append(s.room);
        if (!s.group.isEmpty()) m.append('\n').append("Группа: ").append(s.group);
        m.append('\n').append(s.upper ? "Верхняя" : "Нижняя").append(" неделя");
        if (!s.dateRange.isEmpty()) m.append('\n').append('\n').append(s.dateRange);
        if (!s.dates.isEmpty()) {
            m.append('\n');
            for (int i = 0; i < s.dates.size(); i++) {
                if (i > 0) m.append(i % 4 == 0 ? '\n' : ", ");
                m.append(s.dates.get(i));
            }
        }
        if (!s.teacher.isEmpty()) m.append('\n').append('\n').append("Преподаватель: ").append(s.teacher);

        final Maps.Building bld = Maps.ofRoom(s.room);
        CharSequence message = m;
        if (bld != null) {
            String line = "\n\n🗺 " + Maps.label(bld, s.room)
                    + (bld.url != null ? " — на карте" : " — как добраться");
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(m);
            int at = sb.length();
            sb.append(line);
            sb.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    Maps.show(MainActivity.this, bld);
                }
            }, at, sb.length(), 0);
            message = sb;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(s.subject);
        b.setMessage(message);
        b.setNeutralButton("О предмете", (d, w) -> showSubject(s.subject));
        if (!s.teacher.isEmpty())
            b.setPositiveButton("Преподаватель", (d, w) -> showTeacher(s));
        b.setNegativeButton("Закрыть", null);
        AlertDialog dlg = b.show();
        TextView body = dlg.findViewById(android.R.id.message);
        if (body != null && bld != null) body.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /** All occurrences of a subject in the loaded schedule. */
    private void showSubject(String subject) {
        List<Slot> of = new ArrayList<>();
        for (Slot s : slots) if (s.subject.equals(subject)) of.add(s);
        Collections.sort(of);
        showSlotList(subject, of);
    }

    /**
     * Teacher info: their full schedule across all groups, fetched from
     * smtu.ru by the viewperson id found in the schedule page. Falls back to
     * their lessons within this group's schedule.
     */
    private void showTeacher(Slot lesson) {
        String teacher = lesson.teacher;
        if (lesson.teacherId.isEmpty()) {
            List<Slot> of = new ArrayList<>();
            for (Slot s : slots) if (s.teacher.equals(teacher)) of.add(s);
            Collections.sort(of);
            showSlotList(teacher, of);
            return;
        }
        final AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle(teacher)
                .setMessage("Загрузка расписания преподавателя…")
                .setNegativeButton("Закрыть", null).create();
        loading.show();
        Smtu.schedule(this, true, lesson.teacherId, (result, error) -> {
            loading.dismiss();
            List<Slot> toShow = (result != null && !result.isEmpty()) ? result : null;
            if (toShow == null) {
                toShow = new ArrayList<>();
                for (Slot s : slots) if (s.teacher.equals(teacher)) toShow.add(s);
                if (error != null) toast(error);
            }
            Collections.sort(toShow);
            showSlotList(teacher, toShow);
        });
    }

    private void showSlotList(String title, List<Slot> of) {
        CharSequence[] items = new CharSequence[of.size()];
        for (int i = 0; i < of.size(); i++) {
            Slot s = of.get(i);
            StringBuilder it = new StringBuilder();
            if (!s.dates.isEmpty()) it.append(s.dates.get(0));
            else it.append(Dates.shortDay(s.day));
            it.append(" · ").append(s.time.replace(" - ", " – "))
              .append(" · ").append(s.subject);
            if (!s.type.isEmpty()) it.append(" (").append(s.type).append(")");
            if (!s.group.isEmpty()) it.append(" · ").append(s.group);
            if (!s.room.isEmpty()) it.append(" · ").append(s.room);
            items[i] = it.toString();
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(title);
        if (of.isEmpty()) b.setMessage("Занятий не найдено.");
        else b.setItems(items, null);
        b.setPositiveButton("Закрыть", null);
        b.show();
    }

    // ------------------------------------------------------------ group picker

    private void showGroupPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);

        EditText search = new EditText(this);
        search.setHint("Группа, например 12826-61");
        box.addView(search, new LinearLayout.LayoutParams(-1, -2));

        final List<Group> all = new ArrayList<>();
        final List<Group> filtered = new ArrayList<>();
        ListView lv = new ListView(this);
        box.addView(lv, new LinearLayout.LayoutParams(-1, dp(320)));

        final android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable e) {
                applyFilter(e.toString(), all, filtered, adapter);
            }
        });

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Ваша группа")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .create();

        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Group picked = filtered.get(pos);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_GID, picked.id).putString(PREF_GNAME, picked.name).apply();
            Smtu.touchRecent(this, picked.id);
            groupId = picked.id;
            groupName = picked.name;
            anchor = Dates.monday(Dates.today());
            dayOffset = (Dates.today().get(Calendar.DAY_OF_WEEK) + 5) % 7;
            dlg.dismiss();
            load();
        });

        dlg.show();

        Smtu.groups(this, (groups, error) -> {
            if (groups.isEmpty()) { toast(error != null ? error : "Список групп недоступен"); return; }
            all.clear();
            all.addAll(groups);
            applyFilter(search.getText().toString(), all, filtered, adapter);
        });
    }

    private void applyFilter(String q, List<Group> all, List<Group> filtered, android.widget.ArrayAdapter<String> adapter) {
        q = q.trim().toLowerCase(RU);
        filtered.clear();
        for (Group g : all)
            if (q.isEmpty() || g.name.toLowerCase(RU).contains(q)) filtered.add(g);
        List<String> names = new ArrayList<>(filtered.size());
        for (Group g : filtered) names.add(g.name);
        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------ about

    private void showInfo() {
        String latest = UpdateChecker.latestKnown(this);
        final boolean newer = latest != null
                && UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME);
        String upd = newer ? "доступна версия " + latest
                : latest == null ? "ещё не проверялось"
                : "установлена последняя версия";
        String link = "gitlab.com/trigger337/smtu-schedule-android-app";
        String text = "Расписание СПбГМТУ " + BuildConfig.VERSION_NAME
                + " (сборка " + BuildConfig.BUILD_DATE + ")\n\n"
                + "Неофициальное приложение-просмотрщик расписания. Все данные "
                + "загружаются напрямую с www.smtu.ru — официального сайта "
                + "университета — при запуске и по кнопке ⟳, после чего "
                + "работают офлайн из кэша на устройстве.\n\n"
                + "Верхняя/нижняя неделя определяется автоматически по данным "
                + "сайта.\n\n"
                + "Тап по названию группы вверху — смена группы.\n\n"
                + "Исходный код: " + link + "\n\n"
                + "Обновления: " + upd + ".";
        SpannableString msg = new SpannableString(text);
        int ls = text.indexOf(link);
        msg.setSpan(new ClickableSpan() {
            @Override public void onClick(View widget) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL)));
                } catch (Exception e) {
                    toast("Браузер не найден");
                }
            }
        }, ls, ls + link.length(), 0);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("О приложении")
                .setMessage(msg);
        if (newer) {
            b.setPositiveButton("Скачать", (d, w) -> startActivity(new Intent(
                    Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL))));
            b.setNegativeButton("Закрыть", null);
        } else {
            b.setPositiveButton("Закрыть", null);
        }
        b.setNeutralButton("Проверить", (d, w) -> UpdateChecker.checkNow(this));
        AlertDialog dlg = b.show();
        TextView body = dlg.findViewById(android.R.id.message);
        if (body != null) body.setMovementMethod(LinkMovementMethod.getInstance());
    }

    // ----------------------------------------------------------------- utils

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
