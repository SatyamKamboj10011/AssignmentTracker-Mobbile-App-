package com.satyam.assignmenttracker.Activities;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.fb.AssignmentReminderReceiver;
import com.satyam.assignmenttracker.models.Base;
import com.satyam.assignmenttracker.Adapters.CourseSelectionAdapter;
import com.satyam.assignmenttracker.models.CourseSummary;
import com.satyam.assignmenttracker.R;

import java.util.List;

/**
 * StudentDashboardActivity — opens a dialog listing enrolled courses when the student
 * taps "View assignments". Selecting a course opens StudentAssignmentsActivity with courseId.
 * Also displays profile photo (photoUrl stored in users/<uid>.photoUrl).
 *
 * Updated: clicking profile area opens ProfileEditActivity.
 */
public class StudentDashboardActivity extends Base {

    TextView tvWelcome, tvRole;
    TextView tvTotal, tvPending, tvCompleted, tvNextDue;

    Button btnLogout, btnViewAssignments;
    ImageView ivProfile; // profile photo

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    private static final int REQ_EDIT_PROFILE = 9001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_dashboard);
        setupQuickToolsBar();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;


        });

        // Bind Views
        tvWelcome = findViewById(R.id.tv_welcome);
        tvRole = findViewById(R.id.tv_role);

        tvTotal = findViewById(R.id.tv_total);
        tvPending = findViewById(R.id.tv_pending);
        tvCompleted = findViewById(R.id.tv_completed);
        tvNextDue = findViewById(R.id.tv_next_due);

        btnLogout = findViewById(R.id.btn_logout);
        btnViewAssignments = findViewById(R.id.btn_view_assignments);

        ivProfile = findViewById(R.id.iv_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Open profile editor when clicking welcome text or profile image
        View.OnClickListener openProfileEditor = v -> {
            FirebaseUser u = mAuth.getCurrentUser();
            if (u == null) {
                Toast.makeText(StudentDashboardActivity.this, "Please sign in", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(StudentDashboardActivity.this, ProfileEditActivity.class);
            startActivityForResult(i, REQ_EDIT_PROFILE);
        };

        if (tvWelcome != null) tvWelcome.setOnClickListener(openProfileEditor);
        if (ivProfile != null) ivProfile.setOnClickListener(openProfileEditor);

        // Show user info & stats
        loadAndShowProfile();

        // View Assignments button opens dialog listing courses
        btnViewAssignments.setOnClickListener(v -> {
            FirebaseUser u = mAuth.getCurrentUser();
            if (u == null) {
                Toast.makeText(StudentDashboardActivity.this, "Please sign in", Toast.LENGTH_SHORT).show();
                return;
            }
            showCoursesDialog(u.getUid());
        });

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(StudentDashboardActivity.this, LoginActivity.class));
            finish();
        });

        Button btnAnnouncements = findViewById(R.id.btn_announcements);
        if (btnAnnouncements != null) {
            btnAnnouncements.setOnClickListener(v -> {
                startActivity(new Intent(StudentDashboardActivity.this, AnnouncementActivity.class));
            });
        }

    }



    @Override
    protected void onResume() {
        super.onResume();
        // refresh profile info in case it was changed in ProfileEditActivity
        loadAndShowProfile();
    }

    private void testNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        String channelId = "assignments_channel";
        String channelName = "Assignments Alerts";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Assignment Reminder")
                .setContentText("You have a new assignment due tomorrow!")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(101, builder.build());
    }

    private void sendTestReminder() {
        String fakeId = "TEST_ASSIGNMENT_ID";
        String title = "Demo assignment";
        String dueText = "due in 1 minute";

        Intent intent = new Intent(this, AssignmentReminderReceiver.class);
        intent.putExtra(AssignmentReminderReceiver.EXTRA_ASSIGNMENT_ID, fakeId);
        intent.putExtra(AssignmentReminderReceiver.EXTRA_ASSIGNMENT_TITLE, title);
        intent.putExtra(AssignmentReminderReceiver.EXTRA_DUE_TEXT, dueText);

        sendBroadcast(intent);
    }


    private void loadAndShowProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Show email immediately while Firestore loads full profile
            tvWelcome.setText("Welcome,\n" + user.getEmail());

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc != null && doc.exists()) {
                            String role = doc.getString("role");
                            if (role != null) tvRole.setText("Role: " + role);
                            else tvRole.setText("Role: (not set)");

                            String display = doc.getString("displayName");
                            String email = doc.getString("email");
                            if (!TextUtils.isEmpty(display)) {
                                tvWelcome.setText("Welcome,\n" + display + (email != null ? "\n" + email : ""));
                            } else if (!TextUtils.isEmpty(email)) {
                                tvWelcome.setText("Welcome,\n" + email);
                            }

                            // Load profile photo if available
                            String photo = doc.getString("photoUrl");
                            if (ivProfile != null) {
                                if (!TextUtils.isEmpty(photo)) {
                                    try {
                                        Glide.with(this)
                                                .load(photo)
                                                .placeholder(R.drawable.ic_social_placeholder)
                                                .error(R.drawable.ic_social_placeholder)
                                                .into(ivProfile);
                                    } catch (Exception e) {
                                        try {
                                            ivProfile.setImageURI(android.net.Uri.parse(photo));
                                        } catch (Exception ignored) {}
                                    }
                                } else {
                                    ivProfile.setImageResource(R.drawable.ic_social_placeholder);
                                }
                            }

                        } else {
                            tvRole.setText("Role: (not set)");
                        }
                    })
                    .addOnFailureListener(e -> tvRole.setText("Role: (error)"));

            // Load student statistics
            loadStudentStats(user.getUid());
        } else {
            tvWelcome.setText("Not Signed In");
            tvRole.setText("");
        }
    }

    // Show dialog containing a scrollable list of enrolled courses (title + counts)
    private void showCoursesDialog(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    List<String> enrolled = (List<String>) userDoc.get("enrolledCourses");
                    if (enrolled == null || enrolled.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle("Your courses")
                                .setMessage("You are not enrolled in any courses.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_courses, null);
                    androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.rvCourses);
                    List<CourseSummary> courseList = new java.util.ArrayList<>();
                    CourseSelectionAdapter adapter = new CourseSelectionAdapter(courseList, course -> {
                        // open assignments for selected course
                        Intent i = new Intent(StudentDashboardActivity.this, StudentAssignmentsActivity.class);
                        i.putExtra("courseId", course.courseId);
                        startActivity(i);
                    });

                    rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
                    rv.setAdapter(adapter);

                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setView(dialogView)
                            .setNegativeButton("Close", (d, w) -> d.dismiss())
                            .create();
                    dialog.show();

                    // load each course summary (title + assigned count + next due)
                    for (String cid : enrolled) {
                        if (cid == null || cid.trim().isEmpty()) continue;
                        final String courseId = cid;

                        db.collection("courses").document(courseId).get()
                                .addOnSuccessListener(courseDoc -> {
                                    String title = (courseDoc != null && courseDoc.exists())
                                            ? courseDoc.getString("title")
                                            : "Course";

                                    db.collection("assignments")
                                            .whereEqualTo("courseId", courseId)
                                            .orderBy("dueDateMillis", Query.Direction.ASCENDING)
                                            .get()
                                            .addOnSuccessListener(qs -> {
                                                int count = 0;
                                                long nextDue = Long.MAX_VALUE;
                                                long now = System.currentTimeMillis();

                                                for (DocumentSnapshot a : qs.getDocuments()) {
                                                    Boolean assignAll = a.getBoolean("assignToAll");
                                                    if (Boolean.TRUE.equals(assignAll)) {
                                                        count++;
                                                    } else {
                                                        List<String> assignedTo = (List<String>) a.get("assignedTo");
                                                        if (assignedTo != null && assignedTo.contains(uid)) {
                                                            count++;
                                                        }
                                                    }
                                                    Long due = a.getLong("dueDateMillis");
                                                    if (due != null && due > now && due < nextDue) nextDue = due;
                                                }

                                                String nextDueText = (nextDue == Long.MAX_VALUE)
                                                        ? "None"
                                                        : android.text.format.DateFormat.format("dd MMM yyyy", nextDue).toString();

                                                courseList.add(new CourseSummary(courseId, title, count, nextDueText));
                                                adapter.notifyDataSetChanged();
                                            })
                                            .addOnFailureListener(e -> {
                                                courseList.add(new CourseSummary(courseId, title, 0, "None"));
                                                adapter.notifyDataSetChanged();
                                            });

                                })
                                .addOnFailureListener(e -> {
                                    // still add placeholder so list remains consistent
                                    courseList.add(new CourseSummary(courseId, "Course", 0, "None"));
                                    adapter.notifyDataSetChanged();
                                });
                    }

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load your courses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // loadStudentStats (same logic, just calls updated updateUI)
    private void loadStudentStats(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    List<String> enrolled = userDoc != null ? (List<String>) userDoc.get("enrolledCourses") : null;
                    final java.util.Set<String> enrolledSet = new java.util.HashSet<>();
                    if (enrolled != null) {
                        for (String c : enrolled) if (c != null) enrolledSet.add(c);
                    }

                    if (enrolledSet.isEmpty()) {
                        updateUI(0, 0, 0, Long.MAX_VALUE);
                        return;
                    }

                    db.collection("assignments")
                            .orderBy("dueDateMillis", Query.Direction.ASCENDING)
                            .get()
                            .addOnSuccessListener(q -> {
                                List<DocumentSnapshot> docs = q.getDocuments();
                                if (docs.isEmpty()) {
                                    updateUI(0, 0, 0, Long.MAX_VALUE);
                                    return;
                                }

                                final java.util.concurrent.atomic.AtomicInteger totalRelevant =
                                        new java.util.concurrent.atomic.AtomicInteger(0);
                                final java.util.concurrent.atomic.AtomicInteger completedCount =
                                        new java.util.concurrent.atomic.AtomicInteger(0);
                                final java.util.concurrent.atomic.AtomicInteger processed =
                                        new java.util.concurrent.atomic.AtomicInteger(0);
                                final java.util.concurrent.atomic.AtomicLong nextDue =
                                        new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);

                                long now = System.currentTimeMillis();

                                for (DocumentSnapshot d : docs) {
                                    String courseId = d.getString("courseId");
                                    if (courseId == null || !enrolledSet.contains(courseId)) continue;

                                    Boolean assignToAll = d.getBoolean("assignToAll");
                                    if (assignToAll == null) assignToAll = Boolean.FALSE;

                                    boolean assignedToStudent = false;
                                    if (Boolean.TRUE.equals(assignToAll)) assignedToStudent = true;
                                    else {
                                        List<String> assignedTo = (List<String>) d.get("assignedTo");
                                        if (assignedTo != null && assignedTo.contains(uid)) assignedToStudent = true;
                                    }

                                    if (!assignedToStudent) continue;

                                    totalRelevant.incrementAndGet();

                                    Long dueMillis = d.getLong("dueDateMillis");
                                    if (dueMillis != null && dueMillis > now && dueMillis < nextDue.get()) {
                                        nextDue.set(dueMillis);
                                    }

                                    final String assignmentId = d.getId();

                                    db.collection("assignments").document(assignmentId)
                                            .collection("completions").document(uid)
                                            .get()
                                            .addOnSuccessListener(comp -> {
                                                boolean isCompleted = (comp != null && comp.exists());
                                                if (isCompleted) completedCount.incrementAndGet();

                                                db.collection("assignments").document(assignmentId)
                                                        .collection("submissions").document(uid)
                                                        .get()
                                                        .addOnSuccessListener(sub -> {
                                                            if (sub != null && sub.exists()) completedCount.incrementAndGet();
                                                            if (processed.incrementAndGet() == totalRelevant.get()) {
                                                                int total = totalRelevant.get();
                                                                int completed = completedCount.get();
                                                                updateUI(total, Math.max(0, total - completed), completed, nextDue.get());
                                                            }
                                                        })
                                                        .addOnFailureListener(e2 -> {
                                                            if (processed.incrementAndGet() == totalRelevant.get()) {
                                                                int total = totalRelevant.get();
                                                                int completed = completedCount.get();
                                                                updateUI(total, Math.max(0, total - completed), completed, nextDue.get());
                                                            }
                                                        });
                                            })
                                            .addOnFailureListener(e -> {
                                                db.collection("assignments").document(assignmentId)
                                                        .collection("submissions").document(uid)
                                                        .get()
                                                        .addOnSuccessListener(sub -> {
                                                            if (sub != null && sub.exists()) completedCount.incrementAndGet();
                                                            if (processed.incrementAndGet() == totalRelevant.get()) {
                                                                int total = totalRelevant.get();
                                                                int completed = completedCount.get();
                                                                updateUI(total, Math.max(0, total - completed), completed, nextDue.get());
                                                            }
                                                        })
                                                        .addOnFailureListener(e2 -> {
                                                            if (processed.incrementAndGet() == totalRelevant.get()) {
                                                                int total = totalRelevant.get();
                                                                int completed = completedCount.get();
                                                                updateUI(total, Math.max(0, total - completed), completed, nextDue.get());
                                                            }
                                                        });
                                            });
                                }

                                if (totalRelevant.get() == 0) {
                                    updateUI(0, 0, 0, Long.MAX_VALUE);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to load assignments: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load student profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateUI(int total, int pending, int completed, long nextDueDate) {
        // Just numbers, because labels are already in the card layout
        if (tvTotal != null) tvTotal.setText(String.valueOf(total));
        if (tvPending != null) tvPending.setText(String.valueOf(pending));
        if (tvCompleted != null) tvCompleted.setText(String.valueOf(completed));

        if (tvNextDue != null) {
            if (nextDueDate == Long.MAX_VALUE) {
                tvNextDue.setText("None");
            } else {
                tvNextDue.setText(
                        android.text.format.DateFormat.format("dd MMM yyyy", nextDueDate)
                );
            }
        }
    }
}
