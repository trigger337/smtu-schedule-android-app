package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Building floor plans. А/Б/У have one PDF per building (official plans from
 * smtu.ru/ru/page/201, hosted on isu.smtu.ru behind a redirect) — the app
 * just links out to them and the browser's PDF viewer handles zooming.
 * Г/М have no published plans — address fallback dialog.
 */
final class Maps {

    static final class Building {
        final String letter, address, url;

        Building(String letter, String address, String url) {
            this.letter = letter;
            this.address = address;
            this.url = url;
        }
    }

    private static final Building[] TABLE = {
        new Building("А", "Лоцманская ул., 3",
            "https://isu.smtu.ru/doc_tree_file/9340022def0eb135cfe16946c8e678a262a93545ddb0ddbfa3cfcabeec27ce4a/"),
        new Building("Б", "Лоцманская ул., 10-14",
            "https://isu.smtu.ru/doc_tree_file/e556fc798afbfcab2900f2966edc78ad69a1d7660e1f00c381cdd8d7a73b4551/"),
        new Building("У", "Ленинский пр., 101",
            "https://isu.smtu.ru/doc_tree_file/50c1eeb37e952c41b6737c1acccdc1c1b39cea4a604e750df94649707029de15/"),
        new Building("Г", "Кронверкский пр., 5", null),
        new Building("М", "Псковская ул., 23", null),
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

    /** Open the building's plan on the university site, or the address dialog. */
    static void show(final Activity a, final Building b) {
        if (b.url != null) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(b.url)));
            } catch (Exception e) {
                Toast.makeText(a, "Не найден браузер", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle("Корпус " + b.letter)
                .setMessage("Адрес: " + b.address
                        + "\n\nПоэтажные планы этого корпуса не опубликованы.")
                .setPositiveButton("Закрыть", null)
                .show();
    }
}
