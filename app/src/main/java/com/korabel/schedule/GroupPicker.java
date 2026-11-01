package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.korabel.schedule.Smtu.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * Group selection dialog with recent groups chips, real-time filtering,
 * and background group list fetching.
 */
public final class GroupPicker {

    public interface OnGroupSelectedListener {
        void onGroupSelected(Group group);
    }

    private GroupPicker() { }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    public static void show(final Activity activity, final OnGroupSelectedListener listener) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        box.setPadding(pad, pad, pad, 0);

        List<Group> recents = Smtu.recentGroups(activity);
        final AlertDialog[] dlgRef = new AlertDialog[1];

        if (!recents.isEmpty()) {
            TextView recTitle = new TextView(activity);
            recTitle.setText("Недавние группы:");
            recTitle.setTextSize(12);
            recTitle.setTextColor(activity.getColor(R.color.text_meta));
            box.addView(recTitle, new LinearLayout.LayoutParams(-1, -2));

            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(activity);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout recRow = new LinearLayout(activity);
            recRow.setOrientation(LinearLayout.HORIZONTAL);
            for (final Group g : recents) {
                Button chip = new Button(activity);
                chip.setText(g.name);
                chip.setTextSize(12);
                chip.setAllCaps(false);
                chip.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(activity, 36));
                lp.rightMargin = dp(activity, 6);
                chip.setOnClickListener(v -> {
                    if (dlgRef[0] != null) dlgRef[0].dismiss();
                    listener.onGroupSelected(g);
                });
                recRow.addView(chip, lp);
            }
            hsv.addView(recRow, new FrameLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
            hlp.topMargin = dp(activity, 4);
            hlp.bottomMargin = dp(activity, 8);
            box.addView(hsv, hlp);
        }

        EditText search = new EditText(activity);
        search.setHint("Поиск группы, например 12826-61");
        box.addView(search, new LinearLayout.LayoutParams(-1, -2));

        final ProgressBar groupProgress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        groupProgress.setIndeterminate(true);
        groupProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.time)));
        box.addView(groupProgress, new LinearLayout.LayoutParams(-1, dp(activity, 4)));

        final List<Group> all = new ArrayList<>();
        final List<Group> filtered = new ArrayList<>();
        ListView lv = new ListView(activity);
        box.addView(lv, new LinearLayout.LayoutParams(-1, dp(activity, 280)));

        final android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<>());
        lv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable e) {
                applyFilter(e.toString(), all, filtered, adapter);
            }
        });

        dlgRef[0] = new AlertDialog.Builder(activity)
                .setTitle("Ваша группа")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .create();

        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Group picked = filtered.get(pos);
            dlgRef[0].dismiss();
            listener.onGroupSelected(picked);
        });

        dlgRef[0].show();

        Smtu.groups(activity, (groups, error) -> {
            groupProgress.setVisibility(View.GONE);
            if (groups.isEmpty()) {
                Toast.makeText(activity, error != null ? error : "Список групп недоступен", Toast.LENGTH_SHORT).show();
                return;
            }
            all.clear();
            all.addAll(groups);
            applyFilter(search.getText().toString(), all, filtered, adapter);
        });
    }

    private static void applyFilter(String q, List<Group> all, List<Group> filtered, android.widget.ArrayAdapter<String> adapter) {
        q = q.trim().toLowerCase(Dates.RU);
        filtered.clear();
        for (Group g : all)
            if (q.isEmpty() || g.name.toLowerCase(Dates.RU).contains(q)) filtered.add(g);
        List<String> names = new ArrayList<>(filtered.size());
        for (Group g : filtered) names.add(g.name);
        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();
    }
}
