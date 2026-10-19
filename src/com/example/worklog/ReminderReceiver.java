package com.example.worklog;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String DAILY_CHANNEL_ID = "daily_worklog";
    private static final String FOLLOW_UP_CHANNEL_ID = "follow_up_worklog";
    private static final String ACTION_COMPLETE_FOLLOW_UP = "com.example.worklog.COMPLETE_FOLLOW_UP";
    private static final int REQUEST_CODE = 1740;
    private static final int COMPLETE_REQUEST_OFFSET = 200000;

    @Override
    public void onReceive(Context context, Intent intent) {
        long logId = intent.getLongExtra("log_id", 0);
        if (ACTION_COMPLETE_FOLLOW_UP.equals(intent.getAction()) && logId > 0) {
            completeFollowUp(context, logId);
            return;
        }
        if (logId > 0) {
            handleFollowUp(context, logId);
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    DAILY_CHANNEL_ID, "每日工作记录", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("提醒补录当天工作");
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, DAILY_CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("今天有什么值得留痕？")
                .setContentText("花十几秒记一下，年底写总结会轻松很多。")
                .setContentIntent(pendingOpen)
                .setAutoCancel(true);
        manager.notify(REQUEST_CODE, builder.build());
        schedule(context);
    }

    private void completeFollowUp(Context context, long logId) {
        WorkLogDb db = new WorkLogDb(context);
        try {
            db.markFollowDone(logId);
            cancelFollowUp(context, logId);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel((int) logId);
        } finally {
            db.close();
        }
    }

    private void handleFollowUp(Context context, long logId) {
        WorkLogDb db = new WorkLogDb(context);
        Cursor cursor = db.get(logId);
        try {
            if (!cursor.moveToFirst() || cursor.getInt(6) != 1 || cursor.getInt(8) == 1) return;
            String content = cursor.getString(5);
            String actionNote = cursor.getString(9);
            String repeatMode = cursor.getString(10);
            notifyFollowUp(context, logId,
                    actionNote == null || actionNote.trim().isEmpty() ? content : actionNote);
            long next = nextReminder(cursor.getLong(7), repeatMode);
            if (next > 0) {
                db.updateReminder(logId, next);
                scheduleFollowUp(context, logId, next);
            }
        } finally {
            cursor.close();
            db.close();
        }
    }

    private long nextReminder(long previous, String repeatMode) {
        if ("不重复".equals(repeatMode)) return 0;
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(Math.max(previous, System.currentTimeMillis()));
        if ("每天".equals(repeatMode)) next.add(Calendar.DAY_OF_YEAR, 1);
        else if ("每周".equals(repeatMode)) next.add(Calendar.WEEK_OF_YEAR, 1);
        else if ("每月".equals(repeatMode)) next.add(Calendar.MONTH, 1);
        else return 0;
        return next.getTimeInMillis();
    }

    private void notifyFollowUp(Context context, long logId, String content) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    FOLLOW_UP_CHANNEL_ID, "工作随记提醒", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(
                context, (int) logId, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent complete = new Intent(context, ReminderReceiver.class);
        complete.setAction(ACTION_COMPLETE_FOLLOW_UP);
        complete.putExtra("log_id", logId);
        PendingIntent pendingComplete = PendingIntent.getBroadcast(
                context, (int) logId + COMPLETE_REQUEST_OFFSET, complete,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, FOLLOW_UP_CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("有一项工作需要跟进")
                .setContentText(content == null ? "打开工作随记查看" : content)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(content))
                .setContentIntent(pendingOpen)
                .addAction(android.R.drawable.checkbox_on_background, "标为完成", pendingComplete)
                .setAutoCancel(true);
        manager.notify((int) logId, builder.build());
    }

    public static void scheduleFollowUp(Context context, long logId, long remindAt) {
        cancelFollowUp(context, logId);
        if (remindAt <= System.currentTimeMillis()) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent reminder = new Intent(context, ReminderReceiver.class);
        reminder.putExtra("log_id", logId);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, (int) logId, reminder,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pending);
    }

    public static void cancelFollowUp(Context context, long logId) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent reminder = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, (int) logId, reminder,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
    }

    public static void repairFollowUps(Context context) {
        WorkLogDb db = new WorkLogDb(context);
        Cursor cursor = db.listActiveFollowUps();
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long remindAt = cursor.getLong(7);
                String repeatMode = cursor.getString(10);
                if (remindAt <= 0) continue;
                if (remindAt <= System.currentTimeMillis()) {
                    remindAt = nextFutureReminder(remindAt, repeatMode);
                    if (remindAt <= 0) continue;
                    db.updateReminder(id, remindAt);
                }
                scheduleFollowUp(context, id, remindAt);
            }
        } finally {
            cursor.close();
            db.close();
        }
    }

    private static long nextFutureReminder(long previous, String repeatMode) {
        if ("不重复".equals(repeatMode)) return 0;
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(previous);
        while (next.getTimeInMillis() <= System.currentTimeMillis()) {
            if ("每天".equals(repeatMode)) next.add(Calendar.DAY_OF_YEAR, 1);
            else if ("每周".equals(repeatMode)) next.add(Calendar.WEEK_OF_YEAR, 1);
            else if ("每月".equals(repeatMode)) next.add(Calendar.MONTH, 1);
            else return 0;
        }
        return next.getTimeInMillis();
    }

    public static void schedule(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 17);
        next.set(Calendar.MINUTE, 40);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        Intent reminder = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, reminder,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
    }
}
