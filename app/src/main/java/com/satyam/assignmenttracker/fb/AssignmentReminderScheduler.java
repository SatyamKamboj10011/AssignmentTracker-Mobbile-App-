package com.satyam.assignmenttracker.fb;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AssignmentReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    public static void scheduleReminder(Context context,
                                        String assignmentId,
                                        String title,
                                        Long dueMillis) {
        if (assignmentId == null) {
            Log.w(TAG, "scheduleReminder: assignmentId is null, skipping");
            return;
        }

        // 🔥 TEST MODE: ignore dueMillis, fire 30 seconds from NOW
        long now = System.currentTimeMillis();
        long triggerAt = now + 30_000L; // 30 seconds for testing

        Log.d(TAG, "Scheduling reminder for id=" + assignmentId
                + " title=" + title
                + " at " + triggerAt + " (in 30s)");

        Intent intent = new Intent(context, AssignmentReminderReceiver.class);
        intent.putExtra(AssignmentReminderReceiver.EXTRA_ASSIGNMENT_ID, assignmentId);
        intent.putExtra(AssignmentReminderReceiver.EXTRA_ASSIGNMENT_TITLE,
                title != null ? title : "Assignment");
        intent.putExtra(AssignmentReminderReceiver.EXTRA_DUE_TEXT, "test reminder (30s)");

        int requestCode = assignmentId.hashCode();

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.e(TAG, "AlarmManager is null, cannot schedule alarm");
            return;
        }

        am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pi
        );
    }

    public static void cancelReminder(Context context, String assignmentId) {
        if (assignmentId == null) return;

        Intent intent = new Intent(context, AssignmentReminderReceiver.class);
        int requestCode = assignmentId.hashCode();

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pi);
        }
    }
}
