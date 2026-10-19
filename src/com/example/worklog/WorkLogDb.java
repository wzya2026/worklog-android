package com.example.worklog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class WorkLogDb extends SQLiteOpenHelper {
    public static final String DB_NAME = "worklog.db";

    public WorkLogDb(Context context) {
        super(context, DB_NAME, null, 8);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE logs (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "work_date TEXT NOT NULL," +
                "work_time TEXT NOT NULL DEFAULT ''," +
                "entry_type TEXT NOT NULL DEFAULT '工作记录'," +
                "category TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "follow_up INTEGER NOT NULL DEFAULT 0," +
                "remind_at INTEGER NOT NULL DEFAULT 0," +
                "follow_done INTEGER NOT NULL DEFAULT 0," +
                "action_note TEXT NOT NULL DEFAULT ''," +
                "repeat_mode TEXT NOT NULL DEFAULT '不重复'," +
                "created_at INTEGER NOT NULL)");
        createCategories(db);
        createEntryTypes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE logs ADD COLUMN entry_type TEXT NOT NULL DEFAULT '工作留痕'");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE logs ADD COLUMN work_time TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE logs ADD COLUMN remind_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE logs ADD COLUMN follow_done INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE logs ADD COLUMN action_note TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE logs ADD COLUMN repeat_mode TEXT NOT NULL DEFAULT '不重复'");
        }
        if (oldVersion < 6) {
            createCategories(db);
        }
        if (oldVersion < 7) {
            createEntryTypes(db);
        }
        if (oldVersion < 8) {
            renameDefaultEntryType(db);
        }
    }

    public long add(String date, String time, String entryType, String category, String content,
                    boolean followUp, long remindAt, boolean followDone, String actionNote, String repeatMode) {
        ContentValues values = new ContentValues();
        values.put("work_date", date);
        values.put("work_time", time);
        values.put("entry_type", entryType);
        values.put("category", category);
        values.put("content", content);
        values.put("follow_up", followUp ? 1 : 0);
        values.put("remind_at", remindAt);
        values.put("follow_done", followDone ? 1 : 0);
        values.put("action_note", actionNote);
        values.put("repeat_mode", repeatMode);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("logs", null, values);
    }

    public Cursor list() {
        return getReadableDatabase().query(
                "logs",
                columns(),
                null, null, null, null,
                "work_date DESC, created_at DESC");
    }

    public Cursor listByType(String entryType) {
        return getReadableDatabase().query(
                "logs",
                columns(),
                "entry_type = ?", new String[]{entryType}, null, null,
                "work_date DESC, created_at DESC");
    }

    public void delete(long id) {
        getWritableDatabase().delete("logs", "_id = ?", new String[]{String.valueOf(id)});
    }

    public void update(long id, String date, String time, String entryType, String category,
                       String content, boolean followUp, long remindAt, boolean followDone,
                       String actionNote, String repeatMode) {
        ContentValues values = new ContentValues();
        values.put("work_date", date);
        values.put("work_time", time);
        values.put("entry_type", entryType);
        values.put("category", category);
        values.put("content", content);
        values.put("follow_up", followUp ? 1 : 0);
        values.put("remind_at", remindAt);
        values.put("follow_done", followDone ? 1 : 0);
        values.put("action_note", actionNote);
        values.put("repeat_mode", repeatMode);
        getWritableDatabase().update("logs", values, "_id = ?", new String[]{String.valueOf(id)});
    }

    public Cursor get(long id) {
        return getReadableDatabase().query("logs", columns(), "_id = ?",
                new String[]{String.valueOf(id)}, null, null, null);
    }

    public Cursor listActiveFollowUps() {
        return getReadableDatabase().query("logs", columns(),
                "follow_up = 1 AND follow_done = 0", null, null, null,
                "CASE WHEN remind_at = 0 THEN 1 ELSE 0 END, remind_at ASC, created_at DESC");
    }

    public void updateReminder(long id, long remindAt) {
        ContentValues values = new ContentValues();
        values.put("remind_at", remindAt);
        getWritableDatabase().update("logs", values, "_id = ?", new String[]{String.valueOf(id)});
    }

    public void markFollowDone(long id) {
        ContentValues values = new ContentValues();
        values.put("follow_done", 1);
        values.put("remind_at", 0);
        getWritableDatabase().update("logs", values, "_id = ?", new String[]{String.valueOf(id)});
    }

    private String[] columns() {
        return new String[]{"_id", "work_date", "work_time", "entry_type", "category", "content",
                "follow_up", "remind_at", "follow_done", "action_note", "repeat_mode", "created_at"};
    }

    private void createCategories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "sort_order INTEGER NOT NULL DEFAULT 0)");
        String[] defaults = {"日常工作", "会议研讨", "材料报告", "外联协调", "党建综合", "学习培训", "其他"};
        for (int index = 0; index < defaults.length; index++) {
            ContentValues values = new ContentValues();
            values.put("name", defaults[index]);
            values.put("sort_order", index);
            db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private void createEntryTypes(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS entry_types (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "sort_order INTEGER NOT NULL DEFAULT 0)");
        String[] defaults = {"工作记录", "进度更新", "重要备忘"};
        for (int index = 0; index < defaults.length; index++) {
            ContentValues values = new ContentValues();
            values.put("name", defaults[index]);
            values.put("sort_order", index);
            db.insertWithOnConflict("entry_types", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private void renameDefaultEntryType(SQLiteDatabase db) {
        ContentValues type = new ContentValues();
        type.put("name", "工作记录");
        db.update("entry_types", type, "name = ?", new String[]{"工作留痕"});
        ContentValues logs = new ContentValues();
        logs.put("entry_type", "工作记录");
        db.update("logs", logs, "entry_type = ?", new String[]{"工作留痕"});
    }

    public Cursor listCategories() {
        return getReadableDatabase().query("categories", new String[]{"_id", "name"},
                null, null, null, null, "sort_order ASC, _id ASC");
    }

    public boolean addCategory(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("sort_order", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict(
                "categories", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public boolean renameCategory(long id, String name) {
        Cursor cursor = getReadableDatabase().query("categories", new String[]{"name"},
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null);
        String previous = null;
        try {
            if (cursor.moveToFirst()) previous = cursor.getString(0);
        } finally {
            cursor.close();
        }
        ContentValues values = new ContentValues();
        values.put("name", name);
        int updated = getWritableDatabase().updateWithOnConflict(
                "categories", values, "_id = ?", new String[]{String.valueOf(id)}, SQLiteDatabase.CONFLICT_IGNORE);
        if (previous != null && updated > 0) {
            ContentValues logs = new ContentValues();
            logs.put("category", name);
            getWritableDatabase().update("logs", logs, "category = ?", new String[]{previous});
        }
        return updated > 0;
    }

    public boolean deleteCategory(long id) {
        Cursor cursor = listCategories();
        int count;
        try {
            count = cursor.getCount();
        } finally {
            cursor.close();
        }
        if (count <= 1) return false;
        return getWritableDatabase().delete("categories", "_id = ?",
                new String[]{String.valueOf(id)}) > 0;
    }

    public Cursor listEntryTypes() {
        return getReadableDatabase().query("entry_types", new String[]{"_id", "name"},
                null, null, null, null, "sort_order ASC, _id ASC");
    }

    public Cursor listEntryTypesForExport() {
        return getReadableDatabase().rawQuery(
                "SELECT name FROM entry_types UNION SELECT DISTINCT entry_type AS name FROM logs ORDER BY name",
                null);
    }

    public boolean addEntryType(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("sort_order", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict(
                "entry_types", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public boolean renameEntryType(long id, String name) {
        Cursor cursor = getReadableDatabase().query("entry_types", new String[]{"name"},
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null);
        String previous = null;
        try {
            if (cursor.moveToFirst()) previous = cursor.getString(0);
        } finally {
            cursor.close();
        }
        ContentValues values = new ContentValues();
        values.put("name", name);
        int updated = getWritableDatabase().updateWithOnConflict(
                "entry_types", values, "_id = ?", new String[]{String.valueOf(id)}, SQLiteDatabase.CONFLICT_IGNORE);
        if (previous != null && updated > 0) {
            ContentValues logs = new ContentValues();
            logs.put("entry_type", name);
            getWritableDatabase().update("logs", logs, "entry_type = ?", new String[]{previous});
        }
        return updated > 0;
    }

    public boolean deleteEntryType(long id) {
        Cursor cursor = listEntryTypes();
        int count;
        try {
            count = cursor.getCount();
        } finally {
            cursor.close();
        }
        if (count <= 1) return false;
        return getWritableDatabase().delete("entry_types", "_id = ?",
                new String[]{String.valueOf(id)}) > 0;
    }
}
