package com.korabel.schedule;

import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;

import com.korabel.schedule.Smtu.Slot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Generates RFC 5545 iCalendar (.ics) files and Android CalendarContract intents
 * without any third-party dependencies.
 */
public final class CalendarExport {

    private CalendarExport() { }

    /** Builds an RFC 5545 iCalendar (.ics) stream for all group slots in the current semester term. */
    public static String buildIcs(Context ctx, String groupName, List<Slot> all) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//SMTU Schedule//RU\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Расписание ").append(escape(groupName != null ? groupName : "СПбГМТУ")).append("\r\n");
        sb.append("X-WR-TIMEZONE:Europe/Moscow\r\n");

        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dtStamp = utcFormat.format(new Date());

        SimpleDateFormat localIcsFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);

        Calendar day = Dates.monday(Dates.today());
        // Go 2 weeks back and 16 weeks ahead (covers full semester window)
        day.add(Calendar.DAY_OF_YEAR, -14);

        int count = 0;
        for (int d = 0; d < 18 * 7; d++) {
            List<Slot> daySlots = Now.slotsOf(ctx, all, day);
            for (Slot s : daySlots) {
                int startM = Now.startMin(s);
                int endM = Now.endMin(s);
                if (startM < 0) continue;

                Calendar start = (Calendar) day.clone();
                start.set(Calendar.HOUR_OF_DAY, startM / 60);
                start.set(Calendar.MINUTE, startM % 60);
                start.set(Calendar.SECOND, 0);

                Calendar end = (Calendar) day.clone();
                if (endM > startM) {
                    end.set(Calendar.HOUR_OF_DAY, endM / 60);
                    end.set(Calendar.MINUTE, endM % 60);
                    end.set(Calendar.SECOND, 0);
                } else {
                    end.setTimeInMillis(start.getTimeInMillis() + 90 * 60 * 1000);
                }

                String uid = "smtu-" + (groupName != null ? groupName : "sched") + "-"
                        + localIcsFormat.format(start.getTime()) + "-" + (++count) + "@korabel.schedule";

                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:").append(uid).append("\r\n");
                sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
                sb.append("DTSTART:").append(localIcsFormat.format(start.getTime())).append("\r\n");
                sb.append("DTEND:").append(localIcsFormat.format(end.getTime())).append("\r\n");
                sb.append("SUMMARY:").append(escape(s.subject)).append("\r\n");
                if (!s.room.isEmpty()) {
                    sb.append("LOCATION:").append(escape(s.room)).append("\r\n");
                }
                StringBuilder desc = new StringBuilder();
                if (!s.type.isEmpty()) desc.append(s.type);
                if (!s.teacher.isEmpty()) {
                    if (desc.length() > 0) desc.append("\\n");
                    desc.append("Преподаватель: ").append(s.teacher);
                }
                if (!s.group.isEmpty()) {
                    if (desc.length() > 0) desc.append("\\n");
                    desc.append("Группа: ").append(s.group);
                }
                if (desc.length() > 0) {
                    sb.append("DESCRIPTION:").append(escape(desc.toString())).append("\r\n");
                }
                sb.append("STATUS:CONFIRMED\r\n");
                sb.append("END:VEVENT\r\n");
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    /** Creates an Intent to insert an event into the system calendar. */
    public static Intent insertSlotIntent(Slot s, Calendar day) {
        int startM = Now.startMin(s);
        int endM = Now.endMin(s);
        Calendar begin = (Calendar) day.clone();
        if (startM >= 0) {
            begin.set(Calendar.HOUR_OF_DAY, startM / 60);
            begin.set(Calendar.MINUTE, startM % 60);
            begin.set(Calendar.SECOND, 0);
        }
        Calendar finish = (Calendar) day.clone();
        if (endM > startM) {
            finish.set(Calendar.HOUR_OF_DAY, endM / 60);
            finish.set(Calendar.MINUTE, endM % 60);
            finish.set(Calendar.SECOND, 0);
        } else {
            finish.setTimeInMillis(begin.getTimeInMillis() + 90 * 60 * 1000);
        }

        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.dir/event");
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.getTimeInMillis());
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, finish.getTimeInMillis());
        intent.putExtra(CalendarContract.Events.TITLE, s.subject);
        intent.putExtra(CalendarContract.Events.EVENT_LOCATION, s.room);
        StringBuilder desc = new StringBuilder();
        if (!s.type.isEmpty()) desc.append(s.type);
        if (!s.teacher.isEmpty()) desc.append(" · ").append(s.teacher);
        if (!s.group.isEmpty()) desc.append(" (").append(s.group).append(")");
        intent.putExtra(CalendarContract.Events.DESCRIPTION, desc.toString());
        return intent;
    }
}
