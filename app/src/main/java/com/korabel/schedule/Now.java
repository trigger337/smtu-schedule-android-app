package com.korabel.schedule;

import android.content.Context;

import com.korabel.schedule.Smtu.Slot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Current-lesson state machine shared by all widgets. Computes, for "now":
 * is a lesson ongoing (blue), are we in a break / before the first lesson
 * (yellow), or is the day over (fall over to the next day with lessons).
 */
public final class Now {

    public static final int NONE = 0, ONGOING = 1, BREAK = 2, BEFORE = 3, AFTER = 4;

    public static final class State {
        public int kind = NONE;
        public Slot current;      // ongoing lesson (ONGOING)
        public Slot next;         // upcoming lesson (BREAK/BEFORE: today, AFTER: next day with lessons)
        public Calendar nextDay;  // non-null when next is on another day
        public int nowMin;        // minute of day
        public int remainMin;     // ONGOING: till lesson end; BREAK/BEFORE: till next start
        public int gapMin;        // BREAK/BEFORE: length of the gap the ring illustrates
        public List<Slot> today = new ArrayList<>(); // today's slots sorted by start
    }

    private Now() { }

    /** "10:10" -> 610 minutes; -1 if unparseable. */
    public static int hm(String s) {
        try {
            int c = s.indexOf(':');
            return Integer.parseInt(s.substring(0, c).trim()) * 60
                    + Integer.parseInt(s.substring(c + 1).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public static int startMin(Slot s) {
        int d = s.time.indexOf('-');
        return d < 0 ? hm(s.time) : hm(s.time.substring(0, d));
    }

    public static int endMin(Slot s) {
        int d = s.time.indexOf('-');
        return d < 0 ? -1 : hm(s.time.substring(d + 1));
    }

    public static State compute(Context ctx, List<Slot> all) {
        State st = new State();
        Calendar now = Calendar.getInstance();
        st.nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        Calendar day = (Calendar) now.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);

        st.today.addAll(slotsOf(ctx, all, day));
        Collections.sort(st.today, (a, b) -> startMin(a) - startMin(b));

        for (Slot s : st.today) {                      // ongoing?
            int a = startMin(s), b = endMin(s);
            if (a >= 0 && b > a && st.nowMin >= a && st.nowMin < b) {
                st.kind = ONGOING;
                st.current = s;
                st.remainMin = b - st.nowMin;
                return st;
            }
        }
        for (Slot s : st.today) {                      // next today?
            int a = startMin(s);
            if (a >= 0 && a > st.nowMin) {
                st.next = s;
                st.remainMin = a - st.nowMin;
                int prevEnd = 8 * 60;                  // assume the day starts around 08:00
                for (Slot p : st.today) {
                    int b = endMin(p);
                    if (b >= 0 && b <= st.nowMin && b > prevEnd) prevEnd = b;
                }
                st.gapMin = Math.max(1, a - prevEnd);
                st.kind = prevEnd > 8 * 60 ? BREAK : BEFORE;
                return st;
            }
        }
        for (int i = 1; i <= 7; i++) {                 // fall over to the next day with lessons
            Calendar d2 = (Calendar) day.clone();
            d2.add(Calendar.DAY_OF_YEAR, i);
            List<Slot> of = slotsOf(ctx, all, d2);
            if (!of.isEmpty()) {
                Collections.sort(of, (a, b) -> startMin(a) - startMin(b));
                st.kind = AFTER;
                st.next = of.get(0);
                st.nextDay = d2;
                return st;
            }
        }
        return st;                                     // NONE
    }

    /** Slots occurring on a calendar day: exact-date match, or parity+weekday fallback. */
    public static List<Slot> slotsOf(Context ctx, List<Slot> all, Calendar day) {
        String key = Smtu.fmtRu(day);
        String name = new SimpleDateFormat("EEEE", new Locale("ru")).format(day.getTime());
        boolean upper = Smtu.isUpperWeek(ctx, day);
        List<Slot> out = new ArrayList<>();
        for (Slot s : all) {
            if (s.dates.contains(key)) { out.add(s); continue; }
            if (s.dates.isEmpty() && s.upper == upper && s.day.equalsIgnoreCase(name)) out.add(s);
        }
        return out;
    }
}
