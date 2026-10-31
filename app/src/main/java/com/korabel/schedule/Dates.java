package com.korabel.schedule;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Date helpers shared by the activity and the widgets. */
final class Dates {

    static final Locale RU = new Locale("ru");

    private Dates() { }

    /** Calendar of today at 00:00 local. */
    static Calendar today() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    static boolean isToday(Calendar c) {
        Calendar t = today();
        return t.get(Calendar.YEAR) == c.get(Calendar.YEAR)
            && t.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
    }

    static int dayDiffFromToday(Calendar c) {
        Calendar t = today();
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        long diffMs = d.getTimeInMillis() - t.getTimeInMillis();
        return (int) Math.round((double) diffMs / 86400000.0);
    }

    /** "позавчера" (-2), "вчера" (-1), "сегодня" (0), "завтра" (+1), "послезавтра" (+2), or null. */
    static String relativeDayTag(Calendar c) {
        int diff = dayDiffFromToday(c);
        switch (diff) {
            case -2: return "позавчера";
            case -1: return "вчера";
            case 0: return "сегодня";
            case 1: return "завтра";
            case 2: return "послезавтра";
            default: return null;
        }
    }

    /** Monday 00:00 of the week containing c. */
    static Calendar monday(Calendar c) {
        Calendar r = (Calendar) c.clone();
        r.set(Calendar.HOUR_OF_DAY, 0);
        r.set(Calendar.MINUTE, 0);
        r.set(Calendar.SECOND, 0);
        r.set(Calendar.MILLISECOND, 0);
        int shift = (r.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0 .. Sun=6
        r.add(Calendar.DAY_OF_YEAR, -shift);
        return r;
    }

    static Calendar plus(Calendar c, int days) {
        Calendar r = (Calendar) c.clone();
        r.add(Calendar.DAY_OF_YEAR, days);
        return r;
    }

    /** "Понедельник" for a Calendar, to match Slot.day. */
    static String dayName(Calendar c) {
        return new SimpleDateFormat("EEEE", RU).format(c.getTime());
    }

    static String shortDay(String day) {
        return day.length() <= 3 ? day : day.substring(0, 3);
    }
}
