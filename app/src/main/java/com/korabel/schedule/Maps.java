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
        final double lat, lon;

        Building(String letter, String address, String url, double lat, double lon) {
            this.letter = letter;
            this.address = address;
            this.url = url;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static final Building[] TABLE = {
        new Building("А", "Лоцманская ул., 3",
            "https://isu.smtu.ru/doc_tree_file/9340022def0eb135cfe16946c8e678a262a93545ddb0ddbfa3cfcabeec27ce4a/",
            59.9189, 30.2858),
        new Building("Б", "Лоцманская ул., 10-14",
            "https://isu.smtu.ru/doc_tree_file/e556fc798afbfcab2900f2966edc78ad69a1d7660e1f00c381cdd8d7a73b4551/",
            59.9196, 30.2842),
        new Building("У", "Ленинский пр., 101",
            "https://isu.smtu.ru/doc_tree_file/50c1eeb37e952c41b6737c1acccdc1c1b39cea4a604e750df94649707029de15/",
            59.8549, 30.2458),
        new Building("Г", "Кронверкский пр., 5", null,
            59.9547, 30.3204),
        new Building("М", "Псковская ул., 23", null,
            59.9171, 30.2829),
    };

    /** "У 107" / "509 Корпус Г" / "каф.ИЯ Корпус У" -> building entry, or null. */
    static Building ofRoom(String room) {
        if (room == null || room.isEmpty()) return null;
        for (Building b : TABLE) {
            if (room.startsWith(b.letter + " ") || room.equals(b.letter)) return b;
            int i = room.indexOf("Корпус");
            if (i >= 0) {
                int j = room.indexOf(b.letter, i + 6);
                if (j >= 0) return b;
            }
        }
        return null;
    }

    /** Floor from room number: "У 107" -> 1; "509 Корпус Г" -> 5; "каф.ИЯ…" -> -1. */
    static int floorOf(String room) {
        if (room == null) return -1;
        int start = 0;
        if (room.length() >= 3 && Character.isLetter(room.charAt(0)) && room.charAt(1) == ' ') {
            start = 2;
        }
        for (int i = start; i < room.length(); i++) {
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

    /** Open the building dialog with both map and floor plan options. */
    static void show(final Activity a, final Building b) {
        AlertDialog.Builder builder = new AlertDialog.Builder(a)
                .setTitle("Корпус " + b.letter)
                .setMessage("Адрес: " + b.address
                        + (b.url != null ? "\n\nДоступен официальный поэтажный план." : "\n\nПоэтажные планы этого корпуса не опубликованы."))
                .setPositiveButton("На карте", (d, w) -> openMap(a, b))
                .setNegativeButton("Закрыть", null);

        if (b.url != null) {
            builder.setNeutralButton("Поэтажный план", (d, w) -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(b.url)));
                } catch (Exception e) {
                    Toast.makeText(a, "Не найден браузер", Toast.LENGTH_SHORT).show();
                }
            });
        }
        builder.show();
    }

    static void openMap(Activity a, Building b) {
        try {
            Uri uri = Uri.parse("geo:" + b.lat + "," + b.lon + "?q=" + Uri.encode(b.address + ", Санкт-Петербург"));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
            a.startActivity(mapIntent);
        } catch (Exception e) {
            try {
                Uri webUri = Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(b.address + ", Санкт-Петербург"));
                a.startActivity(new Intent(Intent.ACTION_VIEW, webUri));
            } catch (Exception e2) {
                Toast.makeText(a, "Не удалось открыть карты", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
