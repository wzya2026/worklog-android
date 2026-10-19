package com.example.worklog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderReceiver.schedule(context);
        ReminderReceiver.repairFollowUps(context);
    }
}
