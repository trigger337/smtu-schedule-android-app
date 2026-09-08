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

    /** Monday 00:00 of the week containing c. */
    static Calendar monday(Calendar c) {
        Calendar r = (Calendar) c.clone();
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
