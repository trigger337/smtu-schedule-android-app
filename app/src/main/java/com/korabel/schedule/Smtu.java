package com.korabel.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the only authoritative source of СПбГМТУ schedules: www.smtu.ru.
 *
 *   GET /ru/listschedule/                     -> all group ids and names
 *   GET /ru/viewschedule_new/<gid>/           -> full-semester group schedule
 *   GET /ru/viewschedule_new/teacher/<pid>/   -> full-semester teacher schedule
 *                                               (pid = the /ru/viewperson/<pid>/ id
 *                                                found in group schedule links)
 *
 * Pages are server-rendered HTML. The parser targets the "card" view whose
 * structure every schedule page shares:
 *
 *   <div class="js-day-block"> <h2>Понедельник</h2>
 *     <div class="col js-time-card"> <h3><span>08:30 - 10:00</span></h3>
 *       <div class="js-week-container js-week-1|2">     (upper | lower week)
 *         <h3 class="h6 ..."><span>SUBJECT</span></h3>
 *         <p class="small text-muted ..." title="14.09.2026, 28.09.2026, ...">…</p>
 *         <p class="card-text ..."><em>ROOM <small class="text-muted">TYPE</small></em></p>
 *         <a href="/ru/viewperson/100952/"><small>TEACHER</small></a>
 *
 * The title attribute lists the exact dates of every occurrence, which is what
 * makes day/week views exact. Every fetch merges into a per-schedule cache
 * file, so the app works offline and history survives page edits.
 */
public final class Smtu {

    public static final String HOST = "https://www.smtu.ru";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) Chrome/126.0 Mobile";
    private static final String PREF_RECENT = "recent";
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** One class occurrence: a subject at a time slot on a weekday. */
    public static final class Slot implements Comparable<Slot> {
        public String day = "";        // Понедельник..Воскресенье
        public String time = "";       // 08:30 - 10:00
        public boolean upper;          // js-week-1 (верхняя) / js-week-2 (нижняя)
        public String subject = "";
        public String type = "";       // Лекция / Практическое занятие / ...
        public String room = "";       // 167 Корпус У
        public String group = "";      // group name (e.g. 12739-01)
        public String teacher = "";
        public String teacherId = "";  // viewperson id, "" when not linked
        public String dateRange = "";  // "14 сентября — 21 декабря 2026"
        public List<String> dates = new ArrayList<>(); // exact dd.MM.yyyy occurrences

        @Override public int compareTo(Slot o) {
            String a = dates.isEmpty() ? "" : dates.get(0);
            String b = o.dates.isEmpty() ? "" : o.dates.get(0);
            int c = a.compareTo(b);
            return c != 0 ? c : time.compareTo(o.time);
        }

        String key() { return day + "|" + time + "|" + upper + "|" + subject + "|" + type + "|" + room + "|" + group + "|" + dates; }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("day", day).put("time", time).put("upper", upper).put("subject", subject)
             .put("type", type).put("room", room).put("group", group).put("teacher", teacher)
             .put("teacherId", teacherId).put("dateRange", dateRange)
             .put("dates", new JSONArray(dates));
            return o;
        }

        static Slot fromJson(JSONObject o) {
            Slot s = new Slot();
            s.day = o.optString("day"); s.time = o.optString("time"); s.upper = o.optBoolean("upper");
            s.subject = o.optString("subject"); s.type = o.optString("type"); s.room = o.optString("room");
            s.group = o.optString("group");
            s.teacher = o.optString("teacher"); s.teacherId = o.optString("teacherId");
            s.dateRange = o.optString("dateRange");
            JSONArray d = o.optJSONArray("dates");
            if (d != null) for (int i = 0; i < d.length(); i++) s.dates.add(d.optString(i));
            return s;
        }
    }

    public static final class Group {
        public final String id, name;
        Group(String id, String name) { this.id = id; this.name = name; }
    }

    public interface Callback<T> { void done(T result, String error); }

    // ------------------------------------------------------------------ public

    /** All groups from /ru/listschedule/, cache-first. */
    public static void groups(Context ctx, final Callback<List<Group>> cb) {
        migrate(ctx);
        final List<Group> cached = readGroupsCache(ctx);
        if (!cached.isEmpty()) cb.done(cached, null);
        new Thread(() -> {
            try {
                List<Group> fresh = parseGroups(httpGet(HOST + "/ru/listschedule/"));
                writeGroupsCache(ctx, fresh);
                post(fresh, null, cb);
            } catch (Exception e) {
                if (cached.isEmpty()) post(cached, "Нет сети: " + e.getMessage(), cb);
            }
        }).start();
    }

    /** Full-semester schedule (group or teacher); merges fetch into cache. */
    public static void schedule(Context ctx, boolean teacher, String id, Callback<List<Slot>> cb) {
        migrate(ctx);
        new Thread(() -> {
            try {
                String path = teacher ? "/ru/viewschedule_new/teacher/" : "/ru/viewschedule_new/";
                String html = httpGet(HOST + path + id + "/");
                calibrateParity(ctx, html);
                List<Slot> fresh = parseSchedule(html, ctx);
                List<Slot> stored = mergeAndStore(ctx, teacher, id, fresh);
                if (ctx != null) {
                    ctx.getSharedPreferences("sched", Context.MODE_PRIVATE).edit()
                       .putLong("lastSuccess_" + (teacher ? "t_" : "g_") + id, System.currentTimeMillis())
                       .remove("lastError_" + (teacher ? "t_" : "g_") + id)
                       .apply();
                }
                post(stored, null, cb);
            } catch (Exception e) {
                if (ctx != null) {
                    ctx.getSharedPreferences("sched", Context.MODE_PRIVATE).edit()
                       .putLong("lastError_" + (teacher ? "t_" : "g_") + id, System.currentTimeMillis())
                       .putString("lastErrorMsg_" + (teacher ? "t_" : "g_") + id, e.getMessage())
                       .apply();
                }
                List<Slot> cached = readSlotCache(ctx, teacher, id);
                post(cached, cached.isEmpty() ? "Нет сети: " + e.getMessage() : null, cb);
            }
        }).start();
    }

    /**
     * Checks if schedule update failed and the last successful sync is more than 24 hours old.
     */
    public static boolean isScheduleStale(Context ctx, boolean teacher, String id) {
        if (ctx == null || id == null || id.isEmpty()) return false;
        SharedPreferences p = ctx.getSharedPreferences("sched", Context.MODE_PRIVATE);
        String key = (teacher ? "t_" : "g_") + id;
        long lastErr = p.getLong("lastError_" + key, 0);
        if (lastErr == 0) return false;
        long lastOk = p.getLong("lastSuccess_" + key, 0);
        if (lastOk == 0) {
            File f = schedFile(ctx, teacher, id);
            if (f.exists()) lastOk = f.lastModified();
        }
        long now = System.currentTimeMillis();
        return lastOk == 0 || (now - lastOk) > 24L * 3600000L;
    }

    public static String getScheduleUrl(boolean teacher, String id) {
        String path = teacher ? "/ru/viewschedule_new/teacher/" : "/ru/viewschedule_new/";
        return HOST + path + (id != null ? id + "/" : "");
    }

    /** Synchronous cache read for instant restore. */
    public static List<Slot> loadCacheSync(Context ctx, boolean teacher, String id) {
        migrate(ctx);
        return readSlotCache(ctx, teacher, id);
    }

    /**
     * Week parity for a date. The site prints "Сегодня: ... верхняя/нижняя
     * неделя" on every page; every fetch recalibrates the anchor so the
     * computation stays correct across semesters. Fallback: the week of
     * 2026-08-31 is upper.
     */
    public static boolean isUpperWeek(Context ctx, Calendar date) {
        SharedPreferences p = ctx != null ? ctx.getSharedPreferences("sched", Context.MODE_PRIVATE) : null;
        long anchor = p != null ? p.getLong("parityAnchor", 0) : 0;
        boolean anchorUpper = p != null ? p.getBoolean("parityAnchorUpper", true) : true;
        if (anchor == 0) { // constant fallback: Monday 2026-08-31 was upper
            Calendar c = Calendar.getInstance();
            c.set(2026, Calendar.AUGUST, 31, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            anchor = c.getTimeInMillis();
            anchorUpper = true;
        }
        Calendar aCal = Calendar.getInstance();
        aCal.setTimeInMillis(anchor);
        Calendar aMon = Dates.monday(aCal);
        Calendar dMon = Dates.monday(date != null ? date : Dates.today());
        long diff = dMon.getTimeInMillis() - aMon.getTimeInMillis();
        long weeks = Math.round((double) diff / (7.0 * 86400000.0));
        long mod = ((weeks % 2) + 2) % 2;
        return (mod == 0) == anchorUpper;
    }

    // ----------------------------------------------------------------- parsing

    /** /ru/viewschedule_new/&lt;id&gt;/ links with display names, in page order. */
    static List<Group> parseGroups(String html) {
        Pattern link = Pattern.compile("<a[^>]+href=\"/ru/viewschedule_new/(\\d+)/?\"[^>]*>(.*?)</a>", Pattern.DOTALL);
        Map<String, Group> byId = new TreeMap<>();
        Matcher m = link.matcher(html);
        while (m.find()) {
            String name = stripTags(m.group(2)).trim();
            if (!name.isEmpty()) byId.put(m.group(1), new Group(m.group(1), name));
        }
        return new ArrayList<>(byId.values());
    }

    /** Multi-engine parser: table-view primary, card-view secondary, legacy fallback. */
    static List<Slot> parseSchedule(String html) {
        return parseSchedule(html, null);
    }

    static List<Slot> parseSchedule(String html, Context ctx) {
        List<Slot> out = parseTableSchedule(html, ctx);
        if (out.isEmpty()) out = parseCardSchedule(html, ctx);
        if (out.isEmpty()) out = parseLegacySchedule(html);
        Collections.sort(out);
        return out;
    }

    /** Primary engine: parses server-rendered #table-container with full column data. */
    private static List<Slot> parseTableSchedule(String html, Context ctx) {
        List<Slot> out = new ArrayList<>();
        int tIdx = html.indexOf("id=\"table-container\"");
        if (tIdx < 0) return out;
        int endIdx = html.indexOf("</main>", tIdx);
        String tableHtml = endIdx < 0 ? html.substring(tIdx) : html.substring(tIdx, endIdx);

        Matcher dayM = Pattern.compile("<h3[^>]*class=\"[^\"]*h5[^\"]*\"[^>]*>(.*?)</h3>", Pattern.DOTALL).matcher(tableHtml);
        List<int[]> daySpans = new ArrayList<>();
        List<String> dayNames = new ArrayList<>();
        while (dayM.find()) {
            daySpans.add(new int[]{dayM.start(), dayM.end()});
            dayNames.add(stripTags(dayM.group(1)).trim());
        }

        Pattern rowPat = Pattern.compile("<tr[^>]*class=\"[^\"]*js-week-container[^\"]*\"[^>]*id=\"([^\"]+)\"[^>]*>(.*?)</tr>", Pattern.DOTALL);
        Pattern cellPat = Pattern.compile("<(?:td|th)[^>]*>(.*?)</(?:td|th)>", Pattern.DOTALL);
        Pattern timePat = Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})");
        Pattern subjPat = Pattern.compile("<span>(.*?)</span>", Pattern.DOTALL);
        Pattern typePat = Pattern.compile("<small[^>]*class=\"[^\"]*text-muted[^\"]*\"[^>]*>(.*?)</small>", Pattern.DOTALL);
        Pattern smallPat = Pattern.compile("<small(?![^>]*text-muted)[^>]*>(.*?)</small>", Pattern.DOTALL);
        Pattern personPat = Pattern.compile("<a[^>]*href=[\"']/ru/viewperson/(\\d+)/?[\"'][^>]*>(.*?)</a>", Pattern.DOTALL);

        for (int i = 0; i < daySpans.size(); i++) {
            String day = dayNames.get(i);
            int start = daySpans.get(i)[1];
            int end = (i + 1 < daySpans.size()) ? daySpans.get(i + 1)[0] : tableHtml.length();
            String chunk = tableHtml.substring(start, end);

            Matcher rm = rowPat.matcher(chunk);
            while (rm.find()) {
                String weekId = rm.group(1);
                String rowContent = rm.group(2);

                List<String> cells = new ArrayList<>();
                Matcher cm = cellPat.matcher(rowContent);
                while (cm.find()) cells.add(cm.group(1));
                if (cells.size() < 6) continue;

                String time = "";
                Matcher tm = timePat.matcher(cells.get(0));
                if (tm.find()) time = tm.group(1) + " - " + tm.group(2);

                String room = stripTags(cells.get(2)).trim();
                String group = stripTags(cells.get(3)).trim();

                String col4 = cells.get(4);
                String subject = "";
                Matcher sm = subjPat.matcher(col4);
                if (sm.find()) subject = stripTags(sm.group(1)).trim();

                String type = "";
                Matcher tym = typePat.matcher(col4);
                if (tym.find()) type = stripTags(tym.group(1)).trim();

                List<String> notes = new ArrayList<>();
                Matcher snm = smallPat.matcher(col4);
                while (snm.find()) {
                    String note = stripTags(snm.group(1)).trim();
                    if (!note.isEmpty()) notes.add(note);
                }
                StringBuilder dr = new StringBuilder();
                for (String note : notes) {
                    if (dr.length() > 0) dr.append(", ");
                    dr.append(note);
                }
                String dateRange = dr.toString();

                String col5 = cells.get(5);
                String teacher = "";
                String teacherId = "";
                Matcher pm = personPat.matcher(col5);
                if (pm.find()) {
                    teacherId = pm.group(1);
                    teacher = stripTags(pm.group(2)).trim();
                } else {
                    teacher = stripTags(col5).trim();
                }

                if (subject.isEmpty()) continue;

                boolean both = weekId.contains("both");
                boolean up = weekId.contains("up");

                if (both) {
                    Slot s1 = createSlot(day, time, true, subject, type, room, group, teacher, teacherId, dateRange);
                    expandDates(s1, ctx);
                    out.add(s1);
                    Slot s2 = createSlot(day, time, false, subject, type, room, group, teacher, teacherId, dateRange);
                    expandDates(s2, ctx);
                    out.add(s2);
                } else {
                    Slot s = createSlot(day, time, up, subject, type, room, group, teacher, teacherId, dateRange);
                    expandDates(s, ctx);
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Secondary engine: parses server-rendered #card-container as a fallback. */
    private static List<Slot> parseCardSchedule(String html, Context ctx) {
        List<Slot> out = new ArrayList<>();
        int cIdx = html.indexOf("id=\"card-container\"");
        if (cIdx < 0) return out;
        int endIdx = html.indexOf("id=\"table-container\"", cIdx);
        if (endIdx < 0) endIdx = html.indexOf("</main>", cIdx);
        String cardHtml = endIdx < 0 ? html.substring(cIdx) : html.substring(cIdx, endIdx);

        Matcher dayM = Pattern.compile("<h2[^>]*class=\"[^\"]*h3[^\"]*\"[^>]*>(.*?)</h2>", Pattern.DOTALL).matcher(cardHtml);
        List<int[]> daySpans = new ArrayList<>();
        List<String> dayNames = new ArrayList<>();
        while (dayM.find()) {
            daySpans.add(new int[]{dayM.start(), dayM.end()});
            dayNames.add(stripTags(dayM.group(1)).trim());
        }

        Pattern colPat = Pattern.compile("<div[^>]*class=\"[^\"]*\\bcol\\b[^\"]*\"[^>]*>(.*?)(?=<div[^>]*class=\"[^\"]*\\bcol\\b[^\"]*\"|$)", Pattern.DOTALL);
        Pattern timePat = Pattern.compile("<h3[^>]*class=\"[^\"]*h5[^\"]*\"[^>]*>.*?<span>(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})</span>", Pattern.DOTALL);
        Pattern wkPat = Pattern.compile("<div[^>]*class=\"[^\"]*js-week-container[^\"]*\"[^>]*id=\"([^\"]+)\"[^>]*>(.*?)</div>\\s*(?=(?:<div[^>]*class=\"[^\"]*js-week-container|</div>\\s*</div>|</div>\\s*<div|$))", Pattern.DOTALL);
        Pattern h3Pat = Pattern.compile("<h3[^>]*class=\"[^\"]*h6[^\"]*\"[^>]*>(.*?)</h3>", Pattern.DOTALL);
        Pattern emPat = Pattern.compile("<em>(.*?)</em>", Pattern.DOTALL);
        Pattern typePat = Pattern.compile("<small[^>]*class=\"[^\"]*text-muted[^\"]*\"[^>]*>(.*?)</small>", Pattern.DOTALL);
        Pattern personPat = Pattern.compile("<a[^>]*href=[\"']/ru/viewperson/(\\d+)/?[\"'][^>]*>(.*?)</a>", Pattern.DOTALL);
        Pattern dflexPat = Pattern.compile("<div[^>]*class=\"[^\"]*d-flex align-items-center[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);

        for (int i = 0; i < daySpans.size(); i++) {
            String day = dayNames.get(i);
            int start = daySpans.get(i)[1];
            int end = (i + 1 < daySpans.size()) ? daySpans.get(i + 1)[0] : cardHtml.length();
            String dayChunk = cardHtml.substring(start, end);

            Matcher cm = colPat.matcher(dayChunk);
            while (cm.find()) {
                String cardContent = cm.group(1);
                Matcher tm = timePat.matcher(cardContent);
                if (!tm.find()) continue;
                String time = tm.group(1) + " - " + tm.group(2);

                Matcher wm = wkPat.matcher(cardContent);
                while (wm.find()) {
                    String weekId = wm.group(1);
                    String chunk = wm.group(2);

                    String subject = "";
                    String dateRange = "";
                    Matcher h3m = h3Pat.matcher(chunk);
                    if (h3m.find()) {
                        String h3 = h3m.group(1);
                        String h3Clean = h3.replaceAll("<span[^>]*class=\"[^\"]*float-end[^\"]*\"[^>]*>.*?</span>", "");
                        Matcher sm = Pattern.compile("<span>(.*?)</span>", Pattern.DOTALL).matcher(h3Clean);
                        if (sm.find()) subject = stripTags(sm.group(1)).trim();
                        else subject = stripTags(h3Clean).trim();

                        Matcher snm = Pattern.compile("<small[^>]*>(.*?)</small>", Pattern.DOTALL).matcher(h3);
                        List<String> notes = new ArrayList<>();
                        while (snm.find()) {
                            String note = stripTags(snm.group(1)).trim();
                            if (!note.isEmpty()) notes.add(note);
                        }
                        StringBuilder dr = new StringBuilder();
                        for (String n : notes) {
                            if (dr.length() > 0) dr.append(", ");
                            dr.append(n);
                        }
                        dateRange = dr.toString();
                    }

                    String room = "";
                    String type = "";
                    Matcher em = emPat.matcher(chunk);
                    if (em.find()) {
                        String emText = em.group(1);
                        Matcher tym = typePat.matcher(emText);
                        if (tym.find()) {
                            type = stripTags(tym.group(1)).trim();
                            room = stripTags(emText.substring(0, tym.start())).trim();
                        } else {
                            room = stripTags(emText).trim();
                        }
                    }

                    String teacher = "";
                    String teacherId = "";
                    Matcher pm = personPat.matcher(chunk);
                    if (pm.find()) {
                        teacherId = pm.group(1);
                        teacher = stripTags(pm.group(2)).trim();
                    } else {
                        Matcher dfm = dflexPat.matcher(chunk);
                        if (dfm.find()) teacher = stripTags(dfm.group(1)).trim();
                    }

                    if (subject.isEmpty()) continue;

                    boolean both = weekId.contains("both");
                    boolean up = weekId.contains("up");

                    if (both) {
                        Slot s1 = createSlot(day, time, true, subject, type, room, "", teacher, teacherId, dateRange);
                        expandDates(s1, ctx);
                        out.add(s1);
                        Slot s2 = createSlot(day, time, false, subject, type, room, "", teacher, teacherId, dateRange);
                        expandDates(s2, ctx);
                        out.add(s2);
                    } else {
                        Slot s = createSlot(day, time, up, subject, type, room, "", teacher, teacherId, dateRange);
                        expandDates(s, ctx);
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    /** Legacy fallback parser for old archive HTML. */
    private static List<Slot> parseLegacySchedule(String html) {
        List<Slot> out = new ArrayList<>();
        int i = 0;
        while ((i = html.indexOf("js-day-block", i)) >= 0) {
            int dayEnd = indexOfAny(html, new String[]{"js-day-block", "card-container-end", "</main"}, i + 12);
            if (dayEnd < 0) dayEnd = html.length();
            String block = html.substring(i, dayEnd);

            Matcher h2 = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL).matcher(block);
            String day = h2.find() ? stripTags(h2.group(1)).trim() : "";

            Matcher card = Pattern.compile("js-time-card.*?<span>(\\d\\d:\\d\\d(?:\\s*-\\s*|-)\\d\\d:\\d\\d)</span>(.*?)(?=js-time-card|js-day-block|$)", Pattern.DOTALL).matcher(block);
            while (card.find()) {
                String time = card.group(1).replace(" ", "").replace("-", " - ");
                String cardBody = card.group(2);
                Matcher wk = Pattern.compile("js-week-container js-week-([12])(.*?)(?=js-week-container js-week|$)", Pattern.DOTALL).matcher(cardBody);
                while (wk.find()) {
                    Slot s = parseLessonLegacy(wk.group(2));
                    s.day = day;
                    s.time = time;
                    s.upper = "1".equals(wk.group(1));
                    if (!s.subject.isEmpty()) out.add(s);
                }
            }
            i = dayEnd;
        }
        return out;
    }

    private static Slot parseLessonLegacy(String chunk) {
        Slot s = new Slot();
        Matcher m = Pattern.compile("<h3[^>]*class=\"[^\"]*h6[^\"]*\"[^>]*>\\s*<span>(.*?)</span>", Pattern.DOTALL).matcher(chunk);
        if (m.find()) s.subject = stripTags(m.group(1)).trim();
        m = Pattern.compile("<p[^>]*class=\"[^\"]*text-muted[^\"]*\"[^>]*title=\"([^\"]*)\"").matcher(chunk);
        if (m.find() && !m.group(1).isEmpty())
            for (String d : m.group(1).split("\\s*,\\s*"))
                if (d.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) s.dates.add(d);
        m = Pattern.compile("fa-calendar[^>]*></i>\\s*([^<]*)</p>").matcher(chunk);
        if (m.find()) s.dateRange = m.group(1).trim();
        m = Pattern.compile("<p class=\"card-text[^\"]*\"[^>]*><em>(.*?)</em>", Pattern.DOTALL).matcher(chunk);
        if (m.find()) {
            String em = m.group(1);
            Matcher t = Pattern.compile("<small class=\"text-muted\">([^<]*)</small>").matcher(em);
            if (t.find()) { s.type = t.group(1).trim(); s.room = stripTags(em.substring(0, t.start())).trim(); }
            else s.room = stripTags(em).trim();
        }
        m = Pattern.compile("<a href=\"/ru/viewperson/(\\d+)/\">\\s*<small>(.*?)</small>", Pattern.DOTALL).matcher(chunk);
        if (m.find()) { s.teacherId = m.group(1); s.teacher = stripTags(m.group(2)).trim(); }
        return s;
    }

    private static Slot createSlot(String day, String time, boolean upper, String subject,
                                   String type, String room, String group,
                                   String teacher, String teacherId, String dateRange) {
        Slot s = new Slot();
        s.day = day;
        s.time = time;
        s.upper = upper;
        s.subject = subject;
        s.type = type;
        s.room = room;
        s.group = group;
        s.teacher = teacher;
        s.teacherId = teacherId;
        s.dateRange = dateRange;
        return s;
    }

    /** Generates exact dd.MM.yyyy occurrences for date-bounded classes within the semester. */
    private static void expandDates(Slot s, Context ctx) {
        if (s.dateRange == null || s.dateRange.isEmpty() || s.day == null || s.day.isEmpty()) return;
        int targetDow = dayOfWeek(s.day);
        if (targetDow < 0) return;

        Calendar now = Calendar.getInstance();
        int curYear = now.get(Calendar.YEAR);

        Calendar start = null;
        Calendar end = null;

        Matcher m1 = Pattern.compile("[сСcC]\\s*([0-9]{1,2}(?:\\.[0-9]{1,2}\\.?|\\s+[а-яёА-ЯЁ]+))\\s*(?:по|-|–)\\s*([0-9]{1,2}(?:\\.[0-9]{1,2}\\.?|\\s+[а-яёА-ЯЁ]+(?:\\s+[0-9]{4})?))").matcher(s.dateRange);
        if (m1.find()) {
            start = parseDateText(m1.group(1), curYear);
            end = parseDateText(m1.group(2), curYear);
        } else {
            Matcher m2 = Pattern.compile("([0-9]{1,2}\\.[0-9]{1,2}\\.?)\\s*[-–]\\s*([0-9]{1,2}\\.[0-9]{1,2}(?:\\.[0-9]{4})?)").matcher(s.dateRange);
            if (m2.find()) {
                start = parseDateText(m2.group(1), curYear);
                end = parseDateText(m2.group(2), curYear);
            } else {
                Matcher m3 = Pattern.compile("[сСcC]\\s*([0-9]{1,2}\\.[0-9]{1,2}\\.?)").matcher(s.dateRange);
                if (m3.find()) {
                    start = parseDateText(m3.group(1), curYear);
                    if (start != null) {
                        end = (Calendar) start.clone();
                        end.set(Calendar.MONTH, Calendar.DECEMBER);
                        end.set(Calendar.DAY_OF_MONTH, 31);
                    }
                }
            }
        }

        if (start == null || end == null) return;
        if (end.before(start)) end.add(Calendar.YEAR, 1);

        Calendar cur = (Calendar) start.clone();
        while (!cur.after(end)) {
            if (cur.get(Calendar.DAY_OF_WEEK) == targetDow) {
                if (isUpperWeek(ctx, cur) == s.upper) {
                    s.dates.add(fmtRu(cur));
                }
            }
            cur.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private static Calendar parseDateText(String raw, int curYear) {
        if (raw == null) return null;
        String s = raw.trim();
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();
        try {
            Matcher mDot = Pattern.compile("^([0-9]{1,2})\\.([0-9]{1,2})(?:\\.([0-9]{4}))?$").matcher(s);
            if (mDot.matches()) {
                int d = Integer.parseInt(mDot.group(1));
                int mo = Integer.parseInt(mDot.group(2)) - 1;
                int y = mDot.group(3) != null ? Integer.parseInt(mDot.group(3)) : curYear;
                Calendar c = Calendar.getInstance();
                c.set(y, mo, d, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                return c;
            }
            Matcher mWords = Pattern.compile("^([0-9]{1,2})\\s+([а-яёА-ЯЁ]+)(?:\\s+([0-9]{4}))?$").matcher(s);
            if (mWords.matches()) {
                int d = Integer.parseInt(mWords.group(1));
                int mo = parseRuMonth(mWords.group(2));
                if (mo >= 0) {
                    int y = mWords.group(3) != null ? Integer.parseInt(mWords.group(3)) : curYear;
                    Calendar c = Calendar.getInstance();
                    c.set(y, mo, d, 0, 0, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    return c;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static int parseRuMonth(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.startsWith("янв")) return Calendar.JANUARY;
        if (n.startsWith("фев")) return Calendar.FEBRUARY;
        if (n.startsWith("мар")) return Calendar.MARCH;
        if (n.startsWith("апр")) return Calendar.APRIL;
        if (n.startsWith("ма")) return Calendar.MAY;
        if (n.startsWith("июн")) return Calendar.JUNE;
        if (n.startsWith("июл")) return Calendar.JULY;
        if (n.startsWith("авг")) return Calendar.AUGUST;
        if (n.startsWith("сен")) return Calendar.SEPTEMBER;
        if (n.startsWith("окт")) return Calendar.OCTOBER;
        if (n.startsWith("ноя")) return Calendar.NOVEMBER;
        if (n.startsWith("дек")) return Calendar.DECEMBER;
        return -1;
    }

    private static int dayOfWeek(String dayName) {
        String d = dayName.toLowerCase(Locale.US).trim();
        if (d.startsWith("пон")) return Calendar.MONDAY;
        if (d.startsWith("вто")) return Calendar.TUESDAY;
        if (d.startsWith("сре")) return Calendar.WEDNESDAY;
        if (d.startsWith("чет")) return Calendar.THURSDAY;
        if (d.startsWith("пят")) return Calendar.FRIDAY;
        if (d.startsWith("суб")) return Calendar.SATURDAY;
        if (d.startsWith("вос")) return Calendar.SUNDAY;
        return -1;
    }

    /** "Сегодня: ... верхняя|нижняя неделя" -> parity anchor at today's Monday. */
    static void calibrateParity(Context ctx, String page) {
        Matcher m = Pattern.compile("Сегодня:[^<]*?(верхняя|нижняя)\\s+недел").matcher(page);
        if (!m.find()) return;
        Calendar mon = Dates.monday(Dates.today());
        ctx.getSharedPreferences("sched", Context.MODE_PRIVATE).edit()
           .putLong("parityAnchor", mon.getTimeInMillis())
           .putBoolean("parityAnchorUpper", m.group(1).equals("верхняя"))
           .apply();
    }

    // ------------------------------------------------------------------ cache

    /**
     * Losable data lives in the cache dir: schedules and the group list can
     * all be refetched. The two most recently used group schedules are also
     * mirrored into the files dir, so the app and the widgets survive a cache
     * wipe offline. Teacher schedules are cache-only.
     */

    private static List<Slot> mergeAndStore(Context ctx, boolean teacher, String id, List<Slot> fresh) {
        Map<String, Slot> byKey = new TreeMap<>();
        for (Slot s : readSlotCache(ctx, teacher, id)) byKey.put(s.key(), s);
        for (Slot s : fresh) byKey.put(s.key(), s);
        List<Slot> all = new ArrayList<>(byKey.values());
        Collections.sort(all);
        try {
            JSONArray arr = new JSONArray();
            for (Slot s : all) arr.put(s.toJson());
            File c = schedFile(ctx, teacher, id);
            write(c, new JSONObject().put("slots", arr).toString());
            if (!teacher && recents(ctx).contains(id))
                mirror(c, dataFile(ctx, id));
        } catch (Exception ignored) { }
        return all;
    }

    private static List<Slot> readSlotCache(Context ctx, boolean teacher, String id) {
        List<Slot> out = readSlotsFrom(schedFile(ctx, teacher, id));
        if (!out.isEmpty() || teacher) return out;
        File d = dataFile(ctx, id);                    // cache wiped: fall back to the
        out = readSlotsFrom(d);                        // durable mirror and restore the
        if (!out.isEmpty()) mirror(d, schedFile(ctx, false, id)); // cache from it
        return out;
    }

    private static List<Slot> readSlotsFrom(File f) {
        try {
            if (!f.exists()) return new ArrayList<>();
            JSONArray arr = new JSONObject(read(f)).getJSONArray("slots");
            List<Slot> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) out.add(Slot.fromJson(arr.getJSONObject(i)));
            return out;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private static List<Group> readGroupsCache(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), "groups.json");
            if (!f.exists()) return new ArrayList<>();
            JSONArray arr = new JSONObject(read(f)).getJSONArray("groups");
            List<Group> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Group(o.getString("id"), o.getString("name")));
            }
            return out;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private static void writeGroupsCache(Context ctx, List<Group> groups) {
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) arr.put(new JSONObject().put("id", g.id).put("name", g.name));
            write(new File(ctx.getCacheDir(), "groups.json"), new JSONObject().put("groups", arr).toString());
        } catch (Exception ignored) { }
    }

    static File schedFile(Context ctx, boolean teacher, String id) {
        return new File(ctx.getCacheDir(), (teacher ? "t_" : "g_") + id + ".json");
    }

    private static File dataFile(Context ctx, String id) {
        return new File(ctx.getFilesDir(), "g_" + id + ".json");
    }

    /** Most recently used group ids, newest first, at most two. */
    private static List<String> recents(Context ctx) {
        String v = ctx.getSharedPreferences("sched", Context.MODE_PRIVATE).getString(PREF_RECENT, "");
        List<String> out = new ArrayList<>();
        for (String s : v.split(","))
            if (!s.isEmpty()) out.add(s);
        return out;
    }

    /** Make id the most recent group and mirror the recents into the data dir. */
    static void touchRecent(Context ctx, String id) {
        List<String> ids = recents(ctx);
        ids.remove(id);
        ids.add(0, id);
        while (ids.size() > 2) ids.remove(2);
        StringBuilder sb = new StringBuilder();
        for (String s : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        ctx.getSharedPreferences("sched", Context.MODE_PRIVATE).edit()
           .putString(PREF_RECENT, sb.toString()).apply();
        for (String s : ids) {
            File c = schedFile(ctx, false, s), d = dataFile(ctx, s);
            if (c.exists() && !d.exists()) mirror(c, d);
        }
        File[] strays = ctx.getFilesDir().listFiles((dir, n) -> n.startsWith("g_") && n.endsWith(".json"));
        if (strays != null)
            for (File f : strays)
                if (!ids.contains(f.getName().substring(2, f.getName().length() - 5)))
                    f.delete();
    }

    /** Copy with the original timestamp: a mirror must look exactly as old. */
    private static void mirror(File src, File dst) {
        try {
            write(dst, read(src));
            dst.setLastModified(src.lastModified());
        } catch (Exception ignored) { }
    }

    /** One-time move of the pre-split layout where everything sat in filesDir. */
    private static void migrate(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("sched", Context.MODE_PRIVATE);
        if (p.getBoolean("migrated", false)) return;
        synchronized (Smtu.class) {
            if (p.getBoolean("migrated", false)) return;
            File[] legacy = ctx.getFilesDir().listFiles((dir, n) -> n.equals("groups.json")
                    || ((n.startsWith("g_") || n.startsWith("t_")) && n.endsWith(".json")));
            if (legacy != null)
                for (File f : legacy) {
                    File to = new File(ctx.getCacheDir(), f.getName());
                    if (f.renameTo(to)) continue;
                    try { write(to, read(f)); to.setLastModified(f.lastModified()); f.delete(); }
                    catch (Exception ignored) { }
                }
            File[] plans = ctx.getFilesDir().listFiles((dir, n) -> n.startsWith("map_") && n.endsWith(".pdf"));
            if (plans != null) for (File f : plans) f.delete(); // the in-app viewer is gone
            String gid = p.getString("groupId", null);
            if (gid != null) touchRecent(ctx, gid);
            p.edit().putBoolean("migrated", true).apply();
        }
    }

    // ------------------------------------------------------------------- net

    static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "ru");
        InputStream in = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        in.close();
        c.disconnect();
        return sb.toString();
    }

    private static void write(File f, String s) throws IOException {
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
        w.write(s);
        w.close();
    }

    private static String read(File f) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    // ------------------------------------------------------------------ utils

    static String stripTags(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", " ")
                            .replace("&nbsp;", " ").replace("&amp;", "&")
                            .replace("&laquo;", "«").replace("&raquo;", "»")
                            .replace("&mdash;", "—").replace("&ndash;", "–")
                            .replaceAll("\\s+", " ").trim();
    }

    private static int indexOfAny(String s, String[] keys, int from) {
        int best = -1;
        for (String k : keys) {
            int i = s.indexOf(k, from);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    /** dd.MM.yyyy -> Calendar midnight (for date matching in the UI). */
    public static Calendar parseRu(String dd_mm_yyyy) {
        String[] p = dd_mm_yyyy.split("\\.");
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(p[2]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[0]), 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Calendar -> dd.MM.yyyy. */
    public static String fmtRu(Calendar c) {
        return String.format(Locale.US, "%1$td.%1$tm.%1$tY", c);
    }

    private static <T> void post(final T val, final String err, final Callback<T> cb) {
        UI.post(() -> cb.done(val, err));
    }

    private Smtu() { }
}
