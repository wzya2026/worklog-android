package com.example.worklog;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.security.MessageDigest;
import java.security.SecureRandom;
import android.util.Base64;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
    private static final int EXPORT_XLS_REQUEST = 11;
    private static final int EXPORT_CSV_REQUEST = 14;
    private static final int NOTICE_REQUEST = 13;
    private static final String[] REPEAT_MODES = {
            "不重复", "每天", "每周", "每月"
    };

    private final List<LogItem> items = new ArrayList<>();
    private final List<LogItem> visibleItems = new ArrayList<>();
    private final List<CategoryItem> categories = new ArrayList<>();
    private final List<EntryTypeItem> entryTypes = new ArrayList<>();
    private WorkLogDb db;
    private Spinner categoryInput;
    private LogAdapter adapter;
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean batchMode;
    private Button batchToggle;
    private Button deleteSelected;
    private Button deleteAll;
    private TextView recordsTitle;
    private int browseTimeMode;
    private String browseMonth = "";
    private String browseStartDate = "";
    private String browseEndDate = "";
    private String browseEntryType = "";
    private String browseCategory = "";
    private String searchTerm = "";
    private boolean followOnly;
    private boolean appStarted;
    private Boolean navKeysLayout;
    private Boolean tabletLayout;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new WorkLogDb(this);
        setContentView(buildContent());
        if (isPasswordEnabled()) {
            showUnlockDialog();
        } else {
            startApp();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appStarted) reload();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isTabletLayout()) return;
        setContentView(buildContent());
        if (appStarted) reload();
    }

    @Override
    public void onBackPressed() {
        if (hasBrowseFilters()) {
            resetBrowseFilters();
            return;
        }
        super.onBackPressed();
    }

    private void startApp() {
        if (appStarted) return;
        appStarted = true;
        ReminderReceiver.schedule(this);
        ReminderReceiver.repairFollowUps(this);
        requestNotificationPermission();
        reload();
        showDailyFollowUpSummary();
    }

    private View buildContent() {
        if (isTabletLayout() && isLandscape()) return buildTabletContent();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int bottomPadding = isNavKeysLayout() ? 0 : dp(12);
        page.setPadding(dp(16), dp(14), dp(16), bottomPadding);
        page.setBackgroundColor(Color.rgb(247, 249, 247));

        page.addView(text("工作随记", 24, true));
        TextView subtitle = text("记录、进度、备忘，随手记一句", 14, false);
        subtitle.setTextColor(Color.rgb(80, 90, 85));
        page.addView(subtitle, margins(-1, -2, 0, 2, 0, 12));

        if (isNavKeysLayout()) {
            page.addView(primaryAddButton(), margins(-1, dp(48), 0, 0, 0, 10));
        }

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.addView(button("待跟进", v -> showFollowUpList()),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        firstRow.addView(button("类型", v -> showEntryTypeManager()),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        firstRow.addView(button("分类", v -> showCategoryManager()),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        firstRow.addView(button("安全", v -> showSecuritySettings()),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        page.addView(firstRow, margins(-1, -2, 0, 0, 0, 6));

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.addView(button("分类浏览", v -> showBrowseOptions()),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        secondRow.addView(button("内容检索", v -> showSearchDialog()),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        batchToggle = button("批量删除", v -> toggleBatchMode());
        secondRow.addView(batchToggle, new LinearLayout.LayoutParams(0, dp(44), 1));
        secondRow.addView(button("导出表格", v -> showExportOptions()),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        deleteSelected = button("删除所选", v -> confirmDeleteSelected());
        deleteSelected.setVisibility(View.GONE);
        deleteAll = button("全部删除", v -> confirmDeleteAll());
        deleteAll.setVisibility(View.GONE);
        page.addView(secondRow, margins(-1, -2, 0, 0, 0, 4));
        page.addView(deleteSelected, margins(-1, dp(44), 0, 0, 0, 4));
        page.addView(deleteAll, margins(-1, dp(44), 0, 0, 0, 4));

        recordsTitle = text("最近随手记", 17, true);
        recordsTitle.setOnClickListener(v -> {
            if (hasBrowseFilters()) resetBrowseFilters();
        });
        page.addView(recordsTitle, margins(-1, dp(42), 0, 8, 0, 0));
        ListView list = new ListView(this);
        adapter = new LogAdapter();
        list.setAdapter(adapter);
        list.setDividerHeight(dp(1));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = text("数据保存在本机。删除记录时会再次确认。", 12, false);
        hint.setTextColor(Color.GRAY);
        page.addView(hint, margins(-1, -2, 0, 6, 0, 0));

        if (!isNavKeysLayout()) {
            page.addView(primaryAddButton(), margins(-1, dp(48), 0, 4, 0, 4));
        }
        return page;
    }

    private View buildTabletContent() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(dp(18), dp(14), dp(18), dp(14));
        page.setBackgroundColor(Color.rgb(247, 249, 247));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(0, 0, dp(16), 0);

        tools.addView(text("工作随记", 24, true));
        TextView subtitle = text("平板横屏工作台", 14, false);
        subtitle.setTextColor(Color.rgb(80, 90, 85));
        tools.addView(subtitle, margins(-1, -2, 0, 2, 0, 12));
        tools.addView(primaryAddButton(), margins(-1, dp(48), 0, 0, 0, 10));

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.addView(button("待跟进", v -> showFollowUpList()), new LinearLayout.LayoutParams(0, dp(44), 1));
        firstRow.addView(button("类型", v -> showEntryTypeManager()), new LinearLayout.LayoutParams(0, dp(44), 1));
        tools.addView(firstRow, margins(-1, -2, 0, 0, 0, 6));

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.addView(button("分类", v -> showCategoryManager()), new LinearLayout.LayoutParams(0, dp(44), 1));
        secondRow.addView(button("安全", v -> showSecuritySettings()), new LinearLayout.LayoutParams(0, dp(44), 1));
        tools.addView(secondRow, margins(-1, -2, 0, 0, 0, 6));

        tools.addView(button("分类浏览", v -> showBrowseOptions()), margins(-1, dp(44), 0, 0, 0, 6));
        tools.addView(button("内容检索", v -> showSearchDialog()), margins(-1, dp(44), 0, 0, 0, 6));
        batchToggle = button("批量删除", v -> toggleBatchMode());
        tools.addView(batchToggle, margins(-1, dp(44), 0, 0, 0, 6));
        tools.addView(button("导出表格", v -> showExportOptions()), margins(-1, dp(44), 0, 0, 0, 6));

        deleteSelected = button("删除所选", v -> confirmDeleteSelected());
        deleteSelected.setVisibility(View.GONE);
        tools.addView(deleteSelected, margins(-1, dp(44), 0, 2, 0, 4));
        deleteAll = button("全部删除", v -> confirmDeleteAll());
        deleteAll.setVisibility(View.GONE);
        tools.addView(deleteAll, margins(-1, dp(44), 0, 0, 0, 4));

        TextView hint = text("数据保存在本机。筛选状态下点列表标题可查看全部。", 12, false);
        hint.setTextColor(Color.GRAY);
        tools.addView(hint, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        recordsTitle = text("最近随手记", 17, true);
        recordsTitle.setOnClickListener(v -> {
            if (hasBrowseFilters()) resetBrowseFilters();
        });
        content.addView(recordsTitle, margins(-1, dp(42), 0, 0, 0, 6));
        ListView list = new ListView(this);
        adapter = new LogAdapter();
        list.setAdapter(adapter);
        list.setDividerHeight(dp(1));
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        page.addView(tools, new LinearLayout.LayoutParams(dp(260), -1));
        page.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        return page;
    }

    private Button primaryAddButton() {
        Button addNew = button("录入新工作", v -> showNewEntryDialog());
        addNew.setTextColor(Color.WHITE);
        addNew.setBackgroundColor(Color.rgb(30, 111, 83));
        return addNew;
    }

    private boolean isNavKeysLayout() {
        if (navKeysLayout != null) return navKeysLayout;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            navKeysLayout = info.metaData != null
                    && info.metaData.getBoolean("com.example.worklog.NAV_KEYS_LAYOUT", false);
        } catch (Exception ex) {
            navKeysLayout = false;
        }
        return navKeysLayout;
    }

    private boolean isTabletLayout() {
        if (tabletLayout != null) return tabletLayout;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            tabletLayout = info.metaData != null
                    && info.metaData.getBoolean("com.example.worklog.TABLET_LAYOUT", false);
        } catch (Exception ex) {
            tabletLayout = false;
        }
        return tabletLayout;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void reload() {
        items.clear();
        Cursor cursor = db.list();
        try {
            while (cursor.moveToNext()) {
                items.add(new LogItem(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5),
                        cursor.getInt(6) == 1, cursor.getLong(7), cursor.getInt(8) == 1,
                        cursor.getString(9), cursor.getString(10)));
            }
        } finally {
            cursor.close();
        }
        applyBrowseFilters();
    }

    private void applyBrowseFilters() {
        visibleItems.clear();
        for (LogItem item : items) {
            if (browseTimeMode == 1 && !browseMonth.isEmpty() && !item.date.startsWith(browseMonth)) continue;
            if (browseTimeMode == 2 && !browseStartDate.isEmpty() && item.date.compareTo(browseStartDate) < 0) continue;
            if (browseTimeMode == 2 && !browseEndDate.isEmpty() && item.date.compareTo(browseEndDate) > 0) continue;
            if (!browseEntryType.isEmpty() && !browseEntryType.equals(item.entryType)) continue;
            if (!browseCategory.isEmpty() && !browseCategory.equals(item.category)) continue;
            if (!searchTerm.isEmpty() && !matchesSearch(item, searchTerm)) continue;
            if (followOnly && (!item.followUp || item.followDone)) continue;
            visibleItems.add(item);
        }
        boolean filtered = hasBrowseFilters();
        recordsTitle.setText(followOnly ? "待跟进事项（" + visibleItems.size() + " 条）  查看全部"
                : filtered ? "筛选结果（" + visibleItems.size() + " 条）  查看全部" : "最近随手记");
        recordsTitle.setTextColor(filtered ? Color.rgb(30, 111, 83) : Color.BLACK);
        adapter.notifyDataSetChanged();
    }

    private boolean hasBrowseFilters() {
        return browseTimeMode != 0 || !browseEntryType.isEmpty()
                || !browseCategory.isEmpty() || !searchTerm.isEmpty() || followOnly;
    }

    private void resetBrowseFilters() {
        browseTimeMode = 0;
        browseMonth = "";
        browseStartDate = "";
        browseEndDate = "";
        browseEntryType = "";
        browseCategory = "";
        searchTerm = "";
        followOnly = false;
        if (batchMode) {
            batchMode = false;
            selectedIds.clear();
            batchToggle.setText("批量删除");
            deleteSelected.setVisibility(View.GONE);
            deleteAll.setVisibility(View.GONE);
        }
        applyBrowseFilters();
    }

    private void showFollowUpList() {
        browseTimeMode = 0;
        browseMonth = "";
        browseStartDate = "";
        browseEndDate = "";
        browseEntryType = "";
        browseCategory = "";
        searchTerm = "";
        followOnly = true;
        applyBrowseFilters();
        if (visibleItems.isEmpty()) toast("目前没有未结束的跟进事项");
    }

    private boolean matchesSearch(LogItem item, String keyword) {
        String target = (item.content + " " + item.actionNote + " " + item.entryType + " " + item.category).toLowerCase(Locale.CHINA);
        return target.contains(keyword.toLowerCase(Locale.CHINA));
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setText(searchTerm);
        input.setHint("输入关键词，查找内容和后续举措");
        new AlertDialog.Builder(this)
                .setTitle("内容检索")
                .setView(input)
                .setNeutralButton("清除检索", (dialog, which) -> {
                    searchTerm = "";
                    applyBrowseFilters();
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("检索", (dialog, which) -> {
                    searchTerm = input.getText().toString().trim();
                    applyBrowseFilters();
                }).show();
    }

    private void showBrowseOptions() {
        refreshEntryTypes();
        refreshCategories();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), 0);

        String[] timeModes = {"全部时间", "按月份", "自选日期范围"};
        Spinner timeMode = browseSpinner(form, "时间范围", timeModes, timeModes[browseTimeMode]);

        LinearLayout monthBox = new LinearLayout(this);
        monthBox.setOrientation(LinearLayout.VERTICAL);
        Spinner month = browseSpinner(monthBox, "选择月份", browseChoices("全部月份", 0), browseMonth);
        form.addView(monthBox);

        LinearLayout dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        TextView dateLabel = text("选择起止日期", 14, true);
        dateLabel.setPadding(0, dp(8), 0, 0);
        dateBox.addView(dateLabel);
        EditText startDate = browseDateInput(browseStartDate.isEmpty() ? firstDayOfMonth() : browseStartDate);
        EditText endDate = browseDateInput(browseEndDate.isEmpty() ? today() : browseEndDate);
        dateBox.addView(startDate);
        dateBox.addView(endDate);
        form.addView(dateBox);

        timeMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                monthBox.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                dateBox.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        monthBox.setVisibility(browseTimeMode == 1 ? View.VISIBLE : View.GONE);
        dateBox.setVisibility(browseTimeMode == 2 ? View.VISIBLE : View.GONE);
        Spinner entryType = browseSpinner(form, "按工作类型浏览", browseChoices("全部类型", 1), browseEntryType);
        Spinner category = browseSpinner(form, "按具体分类浏览", browseChoices("全部分类", 2), browseCategory);

        new AlertDialog.Builder(this)
                .setTitle("分类浏览")
                .setView(form)
                .setNeutralButton("恢复全部", (dialog, which) -> resetBrowseFilters())
                .setNegativeButton("取消", null)
                .setPositiveButton("查看", (dialog, which) -> {
                    browseTimeMode = timeMode.getSelectedItemPosition();
                    browseMonth = browseTimeMode == 1 ? browseValue(month) : "";
                    browseStartDate = browseTimeMode == 2 ? startDate.getText().toString() : "";
                    browseEndDate = browseTimeMode == 2 ? endDate.getText().toString() : "";
                    if (!browseStartDate.isEmpty() && !browseEndDate.isEmpty()
                            && browseStartDate.compareTo(browseEndDate) > 0) {
                        String swap = browseStartDate;
                        browseStartDate = browseEndDate;
                        browseEndDate = swap;
                    }
                    browseEntryType = browseValue(entryType);
                    browseCategory = browseValue(category);
                    followOnly = false;
                    applyBrowseFilters();
                }).show();
    }

    private EditText browseDateInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setFocusable(false);
        input.setOnClickListener(v -> chooseDateFor(input));
        return input;
    }

    private String firstDayOfMonth() {
        return new SimpleDateFormat("yyyy-MM-01", Locale.CHINA).format(new Date());
    }

    private Spinner browseSpinner(LinearLayout form, String label, String[] choices, String selected) {
        TextView title = text(label, 14, true);
        title.setPadding(0, dp(8), 0, 0);
        form.addView(title);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
        spinner.setSelection(findIndex(choices, selected.isEmpty() ? choices[0] : selected));
        form.addView(spinner);
        return spinner;
    }

    private String[] browseChoices(String allLabel, int field) {
        List<String> choices = new ArrayList<>();
        choices.add(allLabel);
        Set<String> found = new HashSet<>();
        if (field == 1) {
            for (EntryTypeItem entryType : entryTypes) addBrowseChoice(choices, found, entryType.name);
        } else if (field == 2) {
            for (CategoryItem category : categories) addBrowseChoice(choices, found, category.name);
        }
        for (LogItem item : items) {
            String value;
            if (field == 0) value = item.date.length() >= 7 ? item.date.substring(0, 7) : "";
            else if (field == 1) value = item.entryType;
            else value = item.category;
            addBrowseChoice(choices, found, value);
        }
        return choices.toArray(new String[0]);
    }

    private void addBrowseChoice(List<String> choices, Set<String> found, String value) {
        if (value != null && !value.trim().isEmpty() && found.add(value)) choices.add(value);
    }

    private String browseValue(Spinner spinner) {
        return spinner.getSelectedItemPosition() == 0 ? "" : String.valueOf(spinner.getSelectedItem());
    }

    private void showNewEntryDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), 0);

        Spinner entryType = new Spinner(this);
        refreshEntryTypes();
        entryType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, entryTypeNames()));
        form.addView(entryType);

        EditText date = new EditText(this);
        date.setText(today());
        date.setFocusable(false);
        date.setOnClickListener(v -> chooseDateFor(date));
        form.addView(date);

        EditText time = new EditText(this);
        time.setText(nowTime());
        time.setFocusable(false);
        time.setOnClickListener(v -> chooseTimeFor(time));
        form.addView(time);

        Spinner category = new Spinner(this);
        refreshCategories();
        category.setAdapter(categoryAdapter());
        form.addView(category);

        EditText content = new EditText(this);
        content.setHint("点击输入框，可用手机输入法的麦克风说话");
        content.setGravity(Gravity.TOP);
        content.setMinLines(4);
        form.addView(content);

        CheckBox follow = new CheckBox(this);
        follow.setText("需要后续跟进");
        form.addView(follow);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("录入新工作")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = content.getText().toString().trim();
                    if (value.isEmpty()) {
                        toast("请先输入内容");
                        return;
                    }
                    db.add(date.getText().toString(), time.getText().toString(),
                            String.valueOf(entryType.getSelectedItem()),
                            String.valueOf(category.getSelectedItem()), value, follow.isChecked(),
                            0, false, "", "不重复");
                    dialog.dismiss();
                    reload();
                    toast(follow.isChecked() ? "已保存，可在编辑中设置提醒" : "已保存");
                }));
        dialog.show();
    }

    private void chooseDateFor(EditText input) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) ->
                input.setText(String.format(Locale.CHINA, "%04d-%02d-%02d", year, month + 1, day)),
                now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void chooseTimeFor(EditText input) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) ->
                input.setText(String.format(Locale.CHINA, "%02d:%02d", hour, minute)),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void showExportOptions() {
        new AlertDialog.Builder(this)
                .setTitle("导出表格")
                .setItems(new String[]{"直接保存 Excel 到下载目录", "选择位置导出 Excel", "选择位置导出 CSV"},
                        (dialog, which) -> {
                            if (which == 0) exportToDownloads();
                            else if (which == 1) exportXls();
                            else exportCsv();
                        }).show();
    }

    private void refreshCategories() {
        categories.clear();
        Cursor cursor = db.listCategories();
        try {
            while (cursor.moveToNext()) {
                categories.add(new CategoryItem(cursor.getLong(0), cursor.getString(1)));
            }
        } finally {
            cursor.close();
        }
    }

    private void refreshEntryTypes() {
        entryTypes.clear();
        Cursor cursor = db.listEntryTypes();
        try {
            while (cursor.moveToNext()) {
                entryTypes.add(new EntryTypeItem(cursor.getLong(0), cursor.getString(1)));
            }
        } finally {
            cursor.close();
        }
    }

    private String[] entryTypeNames() {
        String[] names = new String[entryTypes.size()];
        for (int index = 0; index < entryTypes.size(); index++) names[index] = entryTypes.get(index).name;
        return names;
    }

    private void showEntryTypeManager() {
        refreshEntryTypes();
        new AlertDialog.Builder(this)
                .setTitle("管理类型")
                .setItems(entryTypeNames(), (dialog, which) -> editEntryTypeItem(entryTypes.get(which)))
                .setPositiveButton("新增类型", (dialog, which) -> addEntryType())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void addEntryType() {
        EditText input = new EditText(this);
        input.setHint("输入新的类型名称");
        new AlertDialog.Builder(this)
                .setTitle("新增类型")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        toast(db.addEntryType(name) ? "类型已新增" : "已有同名类型");
                    }
                }).show();
    }

    private void editEntryTypeItem(EntryTypeItem entryType) {
        EditText input = new EditText(this);
        input.setText(entryType.name);
        new AlertDialog.Builder(this)
                .setTitle("修改类型")
                .setView(input)
                .setNegativeButton("取消", null)
                .setNeutralButton("删除类型", (dialog, which) -> confirmDeleteEntryType(entryType))
                .setPositiveButton("保存改名", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (db.renameEntryType(entryType.id, name)) {
                            reload();
                            toast("类型已改名");
                        } else {
                            toast("已有同名类型");
                        }
                    }
                }).show();
    }

    private void confirmDeleteEntryType(EntryTypeItem entryType) {
        new AlertDialog.Builder(this)
                .setTitle("删除类型？")
                .setMessage("将删除类型“" + entryType.name + "”。已有历史记录不会被删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (db.deleteEntryType(entryType.id)) {
                        toast("类型已删除");
                    } else {
                        toast("至少保留一个类型");
                    }
                }).show();
    }

    private String[] categoryNames() {
        String[] names = new String[categories.size()];
        for (int index = 0; index < categories.size(); index++) names[index] = categories.get(index).name;
        return names;
    }

    private ArrayAdapter<String> categoryAdapter() {
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categoryNames());
    }

    private void reloadCategorySpinner() {
        if (categoryInput == null) {
            refreshCategories();
            return;
        }
        String selected = categoryInput.getSelectedItem() == null
                ? "" : String.valueOf(categoryInput.getSelectedItem());
        refreshCategories();
        categoryInput.setAdapter(categoryAdapter());
        categoryInput.setSelection(findIndex(categoryNames(), selected));
    }

    private void showCategoryManager() {
        refreshCategories();
        new AlertDialog.Builder(this)
                .setTitle("管理分类")
                .setItems(categoryNames(), (dialog, which) -> editCategoryItem(categories.get(which)))
                .setPositiveButton("新增分类", (dialog, which) -> addCategory())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void addCategory() {
        EditText input = new EditText(this);
        input.setHint("输入新的分类名称");
        new AlertDialog.Builder(this)
                .setTitle("新增分类")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (db.addCategory(name)) {
                            reloadCategorySpinner();
                            toast("分类已新增");
                        } else {
                            toast("已有同名分类");
                        }
                    }
                }).show();
    }

    private void editCategoryItem(CategoryItem category) {
        EditText input = new EditText(this);
        input.setText(category.name);
        new AlertDialog.Builder(this)
                .setTitle("修改分类")
                .setView(input)
                .setNegativeButton("取消", null)
                .setNeutralButton("删除分类", (dialog, which) -> confirmDeleteCategory(category))
                .setPositiveButton("保存改名", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (db.renameCategory(category.id, name)) {
                            reloadCategorySpinner();
                            toast("分类已改名");
                        } else {
                            toast("已有同名分类");
                        }
                    }
                }).show();
    }

    private void confirmDeleteCategory(CategoryItem category) {
        new AlertDialog.Builder(this)
                .setTitle("删除分类？")
                .setMessage("将删除分类“" + category.name + "”。已有历史记录不会被删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (db.deleteCategory(category.id)) {
                        reloadCategorySpinner();
                        toast("分类已删除");
                    } else {
                        toast("至少保留一个分类");
                    }
                }).show();
    }

    private void showDailyFollowUpSummary() {
        SharedPreferences prefs = getSharedPreferences("ui", MODE_PRIVATE);
        String today = today();
        if (!today.equals(prefs.getString("follow_summary_date", ""))) {
            showFollowUpSummary(false);
        }
    }

    private void showFollowUpSummary(boolean manual) {
        Cursor cursor = db.listActiveFollowUps();
        int overdue = 0;
        int todayCount = 0;
        int future = 0;
        List<String> visible = new ArrayList<>();
        long now = System.currentTimeMillis();
        String today = today();
        try {
            while (cursor.moveToNext()) {
                long remindAt = cursor.getLong(7);
                String content = cursor.getString(9).trim().isEmpty() ? cursor.getString(5) : cursor.getString(9);
                if (remindAt > 0 && remindAt < now) overdue++;
                else if (remindAt > 0 && formatDate(remindAt).equals(today)) todayCount++;
                else future++;
                if (visible.size() < 5 && (remindAt <= 0 || remindAt <= endOfToday())) {
                    visible.add((remindAt > 0 ? formatReminder(remindAt) + "  " : "") + content);
                }
            }
        } finally {
            cursor.close();
        }
        int total = overdue + todayCount + future;
        if (total == 0) {
            if (manual) toast("目前没有未结束的跟进事项");
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("逾期事项：").append(overdue).append("\n");
        message.append("今天到期：").append(todayCount).append("\n");
        message.append("其他未结束：").append(future).append("\n");
        if (!visible.isEmpty()) {
            message.append("\n优先事项：\n");
            for (int index = 0; index < visible.size(); index++) {
                message.append(index + 1).append(". ").append(visible.get(index)).append("\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("待跟进提醒")
                .setMessage(message.toString().trim())
                .setNeutralButton("查看列表", (dialog, which) -> showFollowUpList())
                .setNegativeButton("关闭", null)
                .setPositiveButton("今天不再提醒", (dialog, which) ->
                        getSharedPreferences("ui", MODE_PRIVATE).edit()
                                .putString("follow_summary_date", today()).apply())
                .show();
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(time));
    }

    private long endOfToday() {
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        return end.getTimeInMillis();
    }

    private void exportXls() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.ms-excel");
        intent.putExtra(Intent.EXTRA_TITLE, "工作随记_" + today() + ".xls");
        startActivityForResult(intent, EXPORT_XLS_REQUEST);
    }

    private void exportToDownloads() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            exportXls();
            return;
        }
        String fileName = "工作随记_" + today() + "_" + nowFileTime() + ".xls";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WorkLog");
        Uri target = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (target == null) {
            toast("导出失败，请选择其他导出方式");
            return;
        }
        if (writeXls(target)) {
            toast("已保存到 Download/WorkLog/" + fileName);
        } else {
            getContentResolver().delete(target, null, null);
        }
    }

    private void exportCsv() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "工作随记_" + today() + ".csv");
        startActivityForResult(intent, EXPORT_CSV_REQUEST);
    }

    private void writeCsv(Uri target) {
        try (OutputStream output = getContentResolver().openOutputStream(target)) {
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            output.write("日期,时间,模块,类型,内容,需要跟进,后续举措,提醒时间,重复提醒,跟进状态\r\n".getBytes(StandardCharsets.UTF_8));
            Cursor cursor = db.list();
            try {
                while (cursor.moveToNext()) {
                    String row = csv(cursor.getString(1)) + "," + csv(cursor.getString(2)) + "," +
                            csv(cursor.getString(3)) + "," + csv(cursor.getString(4)) + "," +
                            csv(cursor.getString(5)) + "," + (cursor.getInt(6) == 1 ? "是" : "否") + "," +
                            csv(cursor.getString(9)) + "," + csv(formatReminder(cursor.getLong(7))) + "," +
                            csv(cursor.getString(10)) + "," +
                            (cursor.getInt(8) == 1 ? "已结束" : "未结束") + "\r\n";
                    output.write(row.getBytes(StandardCharsets.UTF_8));
                }
            } finally {
                cursor.close();
            }
            toast("CSV 已导出");
        } catch (Exception ex) {
            toast("导出失败，请换一个保存位置");
        }
    }

    private boolean writeXls(Uri target) {
        try (OutputStream output = getContentResolver().openOutputStream(target)) {
            StringBuilder sheet = new StringBuilder();
            sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            sheet.append("<?mso-application progid=\"Excel.Sheet\"?>");
            sheet.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ");
            sheet.append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">");
            sheet.append("<Styles><Style ss:ID=\"Header\"><Font ss:Bold=\"1\"/></Style></Styles>");
            Set<String> usedSheetNames = new HashSet<>();
            Cursor types = db.listEntryTypesForExport();
            try {
                while (types.moveToNext()) {
                    String entryType = types.getString(0);
                    sheet.append("<Worksheet ss:Name=\"").append(xml(excelSheetName(entryType, usedSheetNames))).append("\"><Table>");
                    sheet.append("<Column ss:Width=\"80\"/><Column ss:Width=\"55\"/><Column ss:Width=\"80\"/>");
                    sheet.append("<Column ss:Width=\"360\"/><Column ss:Width=\"70\"/>");
                    sheet.append("<Column ss:Width=\"300\"/><Column ss:Width=\"110\"/><Column ss:Width=\"70\"/><Column ss:Width=\"70\"/>");
                    sheet.append(excelRow(true, "日期", "时间", "类型", "内容", "需要跟进", "后续举措", "提醒时间", "重复提醒", "跟进状态"));
                    Cursor cursor = db.listByType(entryType);
                    try {
                        while (cursor.moveToNext()) {
                            sheet.append(excelRow(false, cursor.getString(1), cursor.getString(2),
                                    cursor.getString(4), cursor.getString(5),
                                    cursor.getInt(6) == 1 ? "是" : "否",
                                    cursor.getString(9),
                                    formatReminder(cursor.getLong(7)),
                                    cursor.getString(10),
                                    cursor.getInt(8) == 1 ? "已结束" : "未结束"));
                        }
                    } finally {
                        cursor.close();
                    }
                    sheet.append("</Table></Worksheet>");
                }
            } finally {
                types.close();
            }
            sheet.append("</Workbook>");
            output.write(sheet.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception ex) {
            toast("导出失败，请换一个保存位置");
            return false;
        }
    }

    private String excelRow(boolean header, String... values) {
        StringBuilder xml = new StringBuilder("<Row>");
        for (String value : values) {
            xml.append("<Cell");
            if (header) xml.append(" ss:StyleID=\"Header\"");
            xml.append("><Data ss:Type=\"String\">").append(xml(value)).append("</Data></Cell>");
        }
        return xml.append("</Row>").toString();
    }

    private String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private String excelSheetName(String value, Set<String> usedNames) {
        String name = value == null ? "" : value.replaceAll("[\\\\/:*?\\[\\]]", "_").trim();
        if (name.isEmpty()) name = "未分类";
        if (name.length() > 31) name = name.substring(0, 31);
        String candidate = name;
        int suffix = 2;
        while (usedNames.contains(candidate)) {
            String tail = "_" + suffix++;
            candidate = name.substring(0, Math.min(name.length(), 31 - tail.length())) + tail;
        }
        usedNames.add(candidate);
        return candidate;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_CSV_REQUEST && resultCode == RESULT_OK && data != null) {
            writeCsv(data.getData());
        } else if (requestCode == EXPORT_XLS_REQUEST && resultCode == RESULT_OK && data != null) {
            if (writeXls(data.getData())) toast("Excel 已导出");
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTICE_REQUEST);
        }
    }

    private boolean isPasswordEnabled() {
        return getSharedPreferences("security", MODE_PRIVATE).contains("password_hash");
    }

    private void showSecuritySettings() {
        if (isPasswordEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("安全设置")
                    .setItems(new String[]{"修改进入密码", "关闭密码保护"}, (dialog, which) -> {
                        if (which == 0) verifyPasswordThen(() -> showSetPasswordDialog("修改进入密码"));
                        else verifyPasswordThen(this::disablePassword);
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("安全设置")
                    .setMessage("开启后，每次重新打开 App 需要输入密码。不加密数据库，但可防止别人拿到手机后直接查看。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("开启密码保护", (dialog, which) -> showSetPasswordDialog("设置进入密码"))
                    .show();
        }
    }

    private void showUnlockDialog() {
        EditText input = passwordInput();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("请输入密码")
                .setView(input)
                .setNegativeButton("退出", (d, which) -> finish())
                .setPositiveButton("进入", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (checkPassword(input.getText().toString())) {
                        dialog.dismiss();
                        startApp();
                    } else {
                        toast("密码不正确");
                    }
                }));
        dialog.show();
    }

    private void verifyPasswordThen(Runnable action) {
        EditText input = passwordInput();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("先验证原密码")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (checkPassword(input.getText().toString())) {
                        dialog.dismiss();
                        action.run();
                    } else {
                        toast("密码不正确");
                    }
                }));
        dialog.show();
    }

    private void showSetPasswordDialog(String title) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), 0);
        EditText first = passwordInput();
        first.setHint("输入 4 位以上密码");
        EditText second = passwordInput();
        second.setHint("再次输入密码");
        form.addView(first);
        form.addView(second);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String password = first.getText().toString();
                    if (password.length() < 4) {
                        toast("密码至少 4 位");
                        return;
                    }
                    if (!password.equals(second.getText().toString())) {
                        toast("两次输入不一致");
                        return;
                    }
                    savePassword(password);
                    dialog.dismiss();
                    toast("密码保护已开启");
                }));
        dialog.show();
    }

    private EditText passwordInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private void savePassword(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltText = Base64.encodeToString(salt, Base64.NO_WRAP);
        getSharedPreferences("security", MODE_PRIVATE).edit()
                .putString("password_salt", saltText)
                .putString("password_hash", hashPassword(password, saltText))
                .apply();
    }

    private boolean checkPassword(String password) {
        SharedPreferences prefs = getSharedPreferences("security", MODE_PRIVATE);
        String salt = prefs.getString("password_salt", "");
        String saved = prefs.getString("password_hash", "");
        return !saved.isEmpty() && saved.equals(hashPassword(password, salt));
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hashed, Base64.NO_WRAP);
        } catch (Exception ex) {
            return "";
        }
    }

    private void disablePassword() {
        getSharedPreferences("security", MODE_PRIVATE).edit()
                .remove("password_salt")
                .remove("password_hash")
                .apply();
        toast("密码保护已关闭");
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    private String nowTime() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }

    private String nowFileTime() {
        return new SimpleDateFormat("HHmmss", Locale.CHINA).format(new Date());
    }

    private String formatReminder(long remindAt) {
        if (remindAt <= 0) return "";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(remindAt));
    }

    private int findIndex(String[] choices, String value) {
        for (int index = 0; index < choices.length; index++) {
            if (choices[index].equals(value)) return index;
        }
        return 0;
    }

    private String[] includeCurrentCategory(String[] choices, String current) {
        for (String choice : choices) {
            if (choice.equals(current)) return choices;
        }
        String[] expanded = new String[choices.length + 1];
        System.arraycopy(choices, 0, expanded, 0, choices.length);
        expanded[choices.length] = current;
        return expanded;
    }

    private void edit(LogItem item) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), 0);

        EditText editDate = new EditText(this);
        editDate.setText(item.date);
        editDate.setFocusable(false);
        editDate.setOnClickListener(v -> {
            chooseDateFor(editDate);
        });
        form.addView(editDate);

        EditText editTime = new EditText(this);
        editTime.setText(item.time);
        editTime.setFocusable(false);
        editTime.setOnClickListener(v -> {
            chooseTimeFor(editTime);
        });
        form.addView(editTime);

        Spinner editEntryType = new Spinner(this);
        refreshEntryTypes();
        String[] editEntryTypeNames = includeCurrentCategory(entryTypeNames(), item.entryType);
        editEntryType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, editEntryTypeNames));
        editEntryType.setSelection(findIndex(editEntryTypeNames, item.entryType));
        form.addView(editEntryType);

        Spinner editCategory = new Spinner(this);
        refreshCategories();
        String[] editCategoryNames = includeCurrentCategory(categoryNames(), item.category);
        editCategory.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, editCategoryNames));
        editCategory.setSelection(findIndex(editCategoryNames, item.category));
        form.addView(editCategory);

        EditText editContent = new EditText(this);
        editContent.setText(item.content);
        editContent.setHint("原始记录或最新进展");
        editContent.setMinLines(4);
        form.addView(editContent);

        EditText editAction = new EditText(this);
        editAction.setText(item.actionNote);
        editAction.setHint("后续跟进举措，例如：联系相关人员，周五前形成材料");
        editAction.setMinLines(3);
        form.addView(editAction);

        CheckBox editFollow = new CheckBox(this);
        editFollow.setText("需要后续跟进");
        editFollow.setChecked(item.followUp);
        form.addView(editFollow);

        CheckBox editDone = new CheckBox(this);
        editDone.setText("已结束跟进");
        editDone.setChecked(item.followDone);
        form.addView(editDone);

        EditText remindInput = new EditText(this);
        remindInput.setFocusable(false);
        remindInput.setHint("点击设置提醒时间");
        remindInput.setText(formatReminder(item.remindAt));
        final long[] remindAt = {item.remindAt};
        remindInput.setOnClickListener(v -> {
            Calendar selected = Calendar.getInstance();
            if (remindAt[0] > 0) selected.setTimeInMillis(remindAt[0]);
            new DatePickerDialog(this, (dateView, year, month, day) -> {
                Calendar withDate = Calendar.getInstance();
                withDate.set(year, month, day);
                new TimePickerDialog(this, (timeView, hour, minute) -> {
                    withDate.set(Calendar.HOUR_OF_DAY, hour);
                    withDate.set(Calendar.MINUTE, minute);
                    withDate.set(Calendar.SECOND, 0);
                    withDate.set(Calendar.MILLISECOND, 0);
                    remindAt[0] = withDate.getTimeInMillis();
                    remindInput.setText(formatReminder(remindAt[0]));
                }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true).show();
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                    selected.get(Calendar.DAY_OF_MONTH)).show();
        });
        form.addView(remindInput);

        Spinner repeatInput = new Spinner(this);
        repeatInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, REPEAT_MODES));
        repeatInput.setSelection(findIndex(REPEAT_MODES, item.repeatMode));
        form.addView(repeatInput);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        new AlertDialog.Builder(this)
                .setTitle("编辑记录")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存修改", (dialog, which) -> {
                    boolean followUp = editFollow.isChecked();
                    boolean done = editDone.isChecked();
                    long reminder = followUp && !done ? remindAt[0] : 0;
                    db.update(item.id, editDate.getText().toString(), editTime.getText().toString(),
                            String.valueOf(editEntryType.getSelectedItem()),
                            String.valueOf(editCategory.getSelectedItem()),
                            editContent.getText().toString().trim(), followUp, reminder, done,
                            editAction.getText().toString().trim(),
                            String.valueOf(repeatInput.getSelectedItem()));
                    if (followUp && !done && reminder > System.currentTimeMillis()) {
                        ReminderReceiver.scheduleFollowUp(this, item.id, reminder);
                    } else {
                        ReminderReceiver.cancelFollowUp(this, item.id);
                    }
                    reload();
                    toast(done ? "已结束跟进" : "修改已保存");
                }).show();
    }

    private void toggleBatchMode() {
        batchMode = !batchMode;
        selectedIds.clear();
        batchToggle.setText(batchMode ? "取消批量" : "批量删除");
        deleteSelected.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        deleteAll.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        updateDeleteSelectedLabel();
        adapter.notifyDataSetChanged();
    }

    private void updateDeleteSelectedLabel() {
        deleteSelected.setText(selectedIds.isEmpty() ? "删除所选" : "删除所选(" + selectedIds.size() + ")");
    }

    private void confirmDeleteSelected() {
        if (selectedIds.isEmpty()) {
            toast("请先勾选要删除的记录");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("确认批量删除？")
                .setMessage("将删除 " + selectedIds.size() + " 条记录，并取消对应提醒。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    for (long id : new HashSet<>(selectedIds)) {
                        ReminderReceiver.cancelFollowUp(this, id);
                        db.delete(id);
                    }
                    selectedIds.clear();
                    batchMode = false;
                    batchToggle.setText("批量删除");
                    deleteSelected.setVisibility(View.GONE);
                    deleteAll.setVisibility(View.GONE);
                    reload();
                    toast("已删除所选记录");
                }).show();
    }

    private void confirmDeleteAll() {
        if (visibleItems.isEmpty()) {
            toast("当前列表没有可删除记录");
            return;
        }
        boolean deletingAllRecords = visibleItems.size() == items.size();
        String scope = deletingAllRecords ? "全部记录" : "当前列表的 " + visibleItems.size() + " 条记录";
        new AlertDialog.Builder(this)
                .setTitle("确认全部删除？")
                .setMessage("将删除" + scope + "，并取消对应提醒。此操作不能撤回，建议先导出表格备份。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    for (LogItem item : new ArrayList<>(visibleItems)) {
                        ReminderReceiver.cancelFollowUp(this, item.id);
                        db.delete(item.id);
                    }
                    selectedIds.clear();
                    batchMode = false;
                    batchToggle.setText("批量删除");
                    deleteSelected.setVisibility(View.GONE);
                    deleteAll.setVisibility(View.GONE);
                    reload();
                    toast("已删除" + scope);
                }).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private class LogAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleItems.size(); }
        @Override public Object getItem(int position) { return visibleItems.get(position); }
        @Override public long getItemId(int position) { return visibleItems.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LogItem item = visibleItems.get(position);
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(8), dp(8), dp(8), dp(8));
            String status = item.followDone ? "  [跟进已结束]" :
                    (item.followUp ? "  [需跟进" + (item.remindAt > 0 ? "：" + formatReminder(item.remindAt) : "") + "]" : "");
            box.addView(text(item.date + " " + item.time + "  [" + item.entryType + "]  " +
                    item.category + status, 13, true));
            if (batchMode) {
                CheckBox selected = new CheckBox(MainActivity.this);
                selected.setText("选中此记录");
                selected.setChecked(selectedIds.contains(item.id));
                selected.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedIds.add(item.id);
                    } else {
                        selectedIds.remove(item.id);
                    }
                    updateDeleteSelectedLabel();
                });
                box.addView(selected);
            }
            TextView body = text(item.content, 15, false);
            body.setPadding(0, dp(4), 0, 0);
            box.addView(body);
            if (item.actionNote != null && !item.actionNote.trim().isEmpty()) {
                TextView action = text("后续举措：" + item.actionNote, 14, false);
                action.setTextColor(Color.rgb(30, 111, 83));
                action.setPadding(0, dp(4), 0, 0);
                box.addView(action);
            }
            if (!batchMode) {
                LinearLayout buttons = new LinearLayout(MainActivity.this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                buttons.addView(button("编辑", v -> edit(item)), margins(dp(72), dp(42), 0, 6, 0, 0));
                if (item.followUp && !item.followDone) {
                    buttons.addView(button("完成跟进", v -> confirmCompleteFollowUp(item)),
                            margins(dp(96), dp(42), 6, 6, 0, 0));
                }
                buttons.addView(button("删除", v -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle("确认删除这条记录？")
                    .setMessage(item.content)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        ReminderReceiver.cancelFollowUp(MainActivity.this, item.id);
                        db.delete(item.id);
                        reload();
                        toast("已删除");
                    }).show()), margins(dp(72), dp(42), 6, 6, 0, 0));
                box.addView(buttons);
            }
            return box;
        }
    }

    private void confirmCompleteFollowUp(LogItem item) {
        String message = item.actionNote == null || item.actionNote.trim().isEmpty()
                ? item.content : item.actionNote;
        new AlertDialog.Builder(this)
                .setTitle("结束这项跟进？")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("完成", (dialog, which) -> {
                    ReminderReceiver.cancelFollowUp(this, item.id);
                    db.markFollowDone(item.id);
                    reload();
                    toast("已结束跟进");
                }).show();
    }

    private static class LogItem {
        final long id;
        final String date;
        final String time;
        final String entryType;
        final String category;
        final String content;
        final boolean followUp;
        final long remindAt;
        final boolean followDone;
        final String actionNote;
        final String repeatMode;

        LogItem(long id, String date, String time, String entryType, String category,
                String content, boolean followUp, long remindAt, boolean followDone,
                String actionNote, String repeatMode) {
            this.id = id;
            this.date = date;
            this.time = time;
            this.entryType = entryType;
            this.category = category;
            this.content = content;
            this.followUp = followUp;
            this.remindAt = remindAt;
            this.followDone = followDone;
            this.actionNote = actionNote;
            this.repeatMode = repeatMode;
        }
    }

    private static class CategoryItem {
        final long id;
        final String name;

        CategoryItem(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class EntryTypeItem {
        final long id;
        final String name;

        EntryTypeItem(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
