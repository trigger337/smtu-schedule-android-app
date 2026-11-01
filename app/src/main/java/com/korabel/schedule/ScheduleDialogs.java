package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.korabel.schedule.Smtu.Slot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Modular dialogs for schedule item details, subject occurrences,
 * teacher schedules, calendar export, and about / sync warnings.
 */
public final class ScheduleDialogs {

    private ScheduleDialogs() { }

    public static void showLesson(final Activity activity, final Slot s, final Calendar day, final List<Slot> allSlots) {
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
        SpannableStringBuilder sb = new SpannableStringBuilder(m);
        if (bld != null) {
            String line = "\n\n🗺 " + Maps.label(bld, s.room)
                    + (bld.url != null ? " — адрес и план" : " — адрес и на карте");
            int at = sb.length();
            sb.append(line);
            sb.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    Maps.show(activity, bld);
                }
            }, at + 2, sb.length(), 0);
        }

        String calLine = "\n\n📅 В календарь устройства";
        int calAt = sb.length();
        sb.append(calLine);
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(View widget) {
                addSlotToCalendar(activity, s, day);
            }
        }, calAt + 2, sb.length(), 0);

        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(s.subject);
        b.setMessage(sb);
        b.setNeutralButton("О предмете", (d, w) -> showSubject(activity, s.subject, allSlots));
        if (!s.teacher.isEmpty())
            b.setPositiveButton("Преподаватель", (d, w) -> showTeacher(activity, s, allSlots));
        b.setNegativeButton("Закрыть", null);
        AlertDialog dlg = b.show();
        TextView body = dlg.findViewById(android.R.id.message);
        if (body != null) body.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public static void addSlotToCalendar(Activity activity, Slot s, Calendar day) {
        try {
            activity.startActivity(CalendarExport.insertSlotIntent(s, day));
        } catch (Exception e) {
            Toast.makeText(activity, "Не удалось открыть календарь: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void showSubject(Activity activity, String subject, List<Slot> allSlots) {
        List<Slot> of = new ArrayList<>();
        if (allSlots != null) {
            for (Slot s : allSlots) if (s.subject.equals(subject)) of.add(s);
        }
        Collections.sort(of);
        showSlotList(activity, subject, of);
    }

    public static void showTeacher(final Activity activity, Slot lesson, final List<Slot> allSlots) {
        final String teacher = lesson.teacher;
        if (lesson.teacherId.isEmpty()) {
            List<Slot> of = new ArrayList<>();
            if (allSlots != null) {
                for (Slot s : allSlots) if (s.teacher.equals(teacher)) of.add(s);
            }
            Collections.sort(of);
            showSlotList(activity, teacher, of);
            return;
        }
        final AlertDialog loading = new AlertDialog.Builder(activity)
                .setTitle(teacher)
                .setMessage("Загрузка расписания преподавателя…")
                .setNegativeButton("Закрыть", null).create();
        loading.show();
        Smtu.schedule(activity, true, lesson.teacherId, (result, error) -> {
            loading.dismiss();
            List<Slot> toShow = (result != null && !result.isEmpty()) ? result : null;
            if (toShow == null) {
                toShow = new ArrayList<>();
                if (allSlots != null) {
                    for (Slot s : allSlots) if (s.teacher.equals(teacher)) toShow.add(s);
                }
                if (error != null) Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
            }
            Collections.sort(toShow);
            showSlotList(activity, teacher, toShow);
        });
    }

    public static void showSlotList(Activity activity, String title, List<Slot> of) {
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
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(title);
        if (of.isEmpty()) b.setMessage("Занятий не найдено.");
        else b.setItems(items, null);
        b.setPositiveButton("Закрыть", null);
        b.show();
    }

    public static void showAbout(final Activity activity, final Runnable onExportCalendar) {
        String latest = UpdateChecker.latestKnown(activity);
        final boolean newer = latest != null
                && UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME);
        String upd = newer ? "доступна версия " + latest
                : latest == null ? "ещё не проверялось"
                : "установлена последняя версия";
        String link = "gitlab.com/trigger337/smtu-schedule-android-app";
        String calExport = "📅 Экспорт расписания в календарь (.ics)";
        String text = "Расписание СПбГМТУ " + BuildConfig.VERSION_NAME
                + " (сборка " + BuildConfig.BUILD_DATE + ")\n\n"
                + "Неофициальное приложение-просмотрщик расписания. Все данные "
                + "загружаются напрямую с www.smtu.ru — официального сайта "
                + "университета — при запуске и по кнопке ⟳, после чего "
                + "работают офлайн из кэша на устройстве.\n\n"
                + "Верхняя/нижняя неделя определяется автоматически по данным "
                + "сайта.\n\n"
                + "Тап по названию группы вверху — смена группы.\n\n"
                + "Календарь: " + calExport + "\n\n"
                + "Исходный код: " + link + "\n\n"
                + "Обновления: " + upd + ".";
        SpannableString msg = new SpannableString(text);
        int ls = text.indexOf(link);
        if (ls >= 0) {
            msg.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL)));
                    } catch (Exception e) {
                        Toast.makeText(activity, "Браузер не найден", Toast.LENGTH_SHORT).show();
                    }
                }
            }, ls, ls + link.length(), 0);
        }
        int cs = text.indexOf(calExport);
        if (cs >= 0) {
            msg.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    if (onExportCalendar != null) onExportCalendar.run();
                }
            }, cs, cs + calExport.length(), 0);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle("О приложении");
        b.setMessage(msg);
        if (newer) {
            b.setPositiveButton("Скачать", (d, w) -> activity.startActivity(new Intent(
                    Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL))));
            b.setNegativeButton("Закрыть", null);
        } else {
            b.setPositiveButton("Закрыть", null);
        }
        b.setNeutralButton("Проверить", (d, w) -> UpdateChecker.checkNow(activity));
        AlertDialog dlg = b.show();
        TextView body = dlg.findViewById(android.R.id.message);
        if (body != null) body.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public static void showExportDialog(final Activity activity, final List<Slot> slots, final Runnable onSaveIcs, final Runnable onShareIcs) {
        if (slots == null || slots.isEmpty()) {
            Toast.makeText(activity, "Расписание не загружено", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Экспорт в календарь")
                .setItems(new CharSequence[] {
                        "Сохранить файл .ics (импорт в любой календарь)",
                        "Поделиться расписанием"
                }, (d, which) -> {
                    if (which == 0) {
                        if (onSaveIcs != null) onSaveIcs.run();
                    } else {
                        if (onShareIcs != null) onShareIcs.run();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public static void showSyncWarning(final Activity activity, String groupId) {
        final String url = Smtu.getScheduleUrl(false, groupId);
        String text = "Расписание не удается обновить более суток из-за проблем с сетью или блокировки подключения.\n\n"
                + "Отображаются сохраненные ранее данные, которые могут быть неактуальны.\n\n"
                + "Актуальное расписание на сайте университета:\n"
                + url;
        SpannableString msg = new SpannableString(text);
        int ls = text.indexOf(url);
        if (ls >= 0) {
            msg.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        Toast.makeText(activity, "Браузер не найден", Toast.LENGTH_SHORT).show();
                    }
                }
            }, ls, ls + url.length(), 0);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle("Расписание устарело");
        b.setMessage(msg);
        b.setPositiveButton("Открыть сайт", (d, w) -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(activity, "Браузер не найден", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Закрыть", null);

        AlertDialog dlg = b.show();
        TextView body = dlg.findViewById(android.R.id.message);
        if (body != null) body.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
