package com.satyam.assignmenttracker.fb;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.satyam.assignmenttracker.Activities.AssignmentDetailsActivity;
import com.satyam.assignmenttracker.Activities.StudentAssignmentsActivity;
import com.satyam.assignmenttracker.R;

public class AssignmentReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_ASSIGNMENT_ID = "extra_assignment_id";
    public static final String EXTRA_ASSIGNMENT_TITLE = "extra_assignment_title";
    public static final String EXTRA_DUE_TEXT = "extra_due_text";

    @Override
    public void onReceive(Context context, Intent intent) {
        String assignmentId = intent.getStringExtra(EXTRA_ASSIGNMENT_ID);
        String title = intent.getStringExtra(EXTRA_ASSIGNMENT_TITLE);
        String dueText = intent.getStringExtra(EXTRA_DUE_TEXT);

        if (title == null) title = "Assignment reminder";
        if (dueText == null) dueText = "";

        // 👉 When user taps the notification, open AssignmentDetailsActivity
        Intent openIntent = new Intent(context, AssignmentDetailsActivity.class);
        openIntent.putExtra("assignmentId", assignmentId);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentPI = PendingIntent.getActivity(
                context,
                assignmentId != null ? assignmentId.hashCode() : (int) System.currentTimeMillis(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // (Optional) second action button → open full assignments list
        Intent listIntent = new Intent(context, StudentAssignmentsActivity.class);
        listIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent listPI = PendingIntent.getActivity(
                context,
                123456, // any fixed ID
                listIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "assignment_reminders")
                .setSmallIcon(R.drawable.ic_notification)  // your vector icon
                .setContentTitle("Assignment due soon")
                .setContentText(title + " • " + dueText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(title + "\n" + dueText))
                .setContentIntent(contentPI)   // tap body → open details
                .addAction(
                        android.R.drawable.ic_menu_view,
                        "All assignments",
                        listPI
                )                               // button: "All assignments"
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int notifId = assignmentId != null ? assignmentId.hashCode() : (int) System.currentTimeMillis();
            nm.notify(notifId, builder.build());
        }
    }
}
