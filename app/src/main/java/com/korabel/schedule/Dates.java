package com.korabel.schedule;

import java.util.Calendar;
import java.util.Locale;

/** Date helpers shared by the activity and the widgets. */
final class Dates {

    static final Locale RU = new Locale("ru");

    private static final String[] RU_DAYS = {
        "воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота"
    };

    private static final String[] RU_MONTHS = {
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

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
        int y1 = t.get(Calendar.YEAR);
        int y2 = c.get(Calendar.YEAR);
        if (y1 == y2) {
            return c.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR);
        }
        long msDiff = c.getTimeInMillis() - t.getTimeInMillis();
        return (int) Math.round((double) msDiff / 86400000.0);
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

    /** "понедельник" for a Calendar, to match Slot.day. */
    static String dayName(Calendar c) {
        return RU_DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** "Понедельник, 26 октября" */
    static String formatHeader(Calendar c) {
        String d = dayName(c);
        char first = Character.toUpperCase(d.charAt(0));
        return first + d.substring(1) + ", " + c.get(Calendar.DAY_OF_MONTH) + " " + RU_MONTHS[c.get(Calendar.MONTH)];
    }

    /** "26 октября" */
    static String formatDayMonth(Calendar c) {
        return c.get(Calendar.DAY_OF_MONTH) + " " + RU_MONTHS[c.get(Calendar.MONTH)];
    }

    /** "26 октября, понедельник" */
    static String formatDayMonthWeekday(Calendar c) {
        return c.get(Calendar.DAY_OF_MONTH) + " " + RU_MONTHS[c.get(Calendar.MONTH)] + ", " + dayName(c);
    }

    static String shortDay(String day) {
        return day.length() <= 3 ? day : day.substring(0, 3);
    }
}
