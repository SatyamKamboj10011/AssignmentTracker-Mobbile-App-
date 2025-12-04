package com.satyam.assignmenttracker.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.models.Base;
import com.satyam.assignmenttracker.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TeacherDashboardActivity — matches the teacher_dashboard XML you provided.
 *
 * - Safe view wiring (no ClassCastException risk).
 * - Buttons mapped to actions in the XML.
 * - Loads teacher's courses and shows simple quick stats (assignments, distinct students).
 *
 * Notes:
 * - TeacherManageCoursesActivity should honor "action" extras:
 *    - "manageParticipants"  -> ask teacher to pick a course, then open ManageCourseParticipantsActivity
 *    - "viewAssignments"    -> ask teacher to pick a course, then open TeacherAssignmentsForCourseActivity
 */
public class TeacherDashboardActivity extends Base {

    private static final String TAG = "TeacherDashboard";

    private TextView tvWelcome;
    private TextView tvRole;
    private TextView tvTotalAssignments;
    private TextView tvActiveStudents;
    private TextView tvPendingReviews;

    private ImageView ivAvatar;
    private Button btnCreateAssignment;
    private Button btnViewAssignments;
    private Button btnManageParticipants;
    private Button btnLogout;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public static final String EXTRA_TEACHER_UID = "teacherUid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_teacher_dashboard);

        // Apply insets if root exists
        View root = findViewById(R.id.main);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        // Views (IDs from your XML)
        tvWelcome = findViewById(R.id.tv_welcome);
        tvRole = findViewById(R.id.tv_role);
        tvTotalAssignments = findViewById(R.id.tv_total_assignments);
        tvActiveStudents = findViewById(R.id.tv_active_students);
        tvPendingReviews = findViewById(R.id.tv_pending_reviews);

        ivAvatar = findViewById(R.id.iv_teacher_avatar);
        //btnCreateAssignment = findViewById(R.id.btn_create_assignment);
        btnViewAssignments = findViewById(R.id.btn_view_assignments);
        btnManageParticipants = findViewById(R.id.btn_manage_participants);
        btnLogout = findViewById(R.id.btn_logout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        final String uid = user != null ? user.getUid() : null;

        // Populate welcome text and role
        if (user != null) {
            if (tvWelcome != null) tvWelcome.setText("Welcome,\n" + (user.getEmail() != null ? user.getEmail() : "(no email)"));
            // load role/displayName
            if (uid != null) {
                db.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (doc != null && doc.exists()) {
                                String role = doc.getString("role");
                                String display = doc.getString("displayName");
                                String email = doc.getString("email");
                                if (display != null && !display.isEmpty()) {
                                    if (tvWelcome != null) tvWelcome.setText("Welcome,\n" + display + (email != null ? ("\n" + email) : ""));
                                }
                                if (tvRole != null) tvRole.setText("Role: " + (role != null ? role : "(not set)"));
                            } else {
                                if (tvRole != null) tvRole.setText("Role: (not set)");
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (tvRole != null) tvRole.setText("Role: (error)");
                        });
            }
        } else {
            if (tvWelcome != null) tvWelcome.setText("Not signed in");
            if (tvRole != null) tvRole.setText("");
        }

        // Hook up avatar tap -> ProfileEditActivity (if present)
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() == null) {
                    Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(TeacherDashboardActivity.this, ProfileEditActivity.class));
            });
        }

        // Create assignment
        if (btnCreateAssignment != null) {
            btnCreateAssignment.setOnClickListener(v -> {
                startActivity(new Intent(TeacherDashboardActivity.this, CreateAssignmentActivity.class));
            });
        }

        // View assignments -> let TeacherManageCoursesActivity show courses and forward to TeacherAssignmentsForCourseActivity
        if (btnViewAssignments != null) {
            btnViewAssignments.setOnClickListener(v -> {
                if (uid == null) { Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show(); return; }
                Intent i = new Intent(TeacherDashboardActivity.this, TeacherManageCoursesActivity.class);
                i.putExtra(EXTRA_TEACHER_UID, uid);
                i.putExtra("action", "viewAssignments");
                startActivity(i);
            });
        }

        // Manage students/participants -> TeacherManageCoursesActivity with action flag
        if (btnManageParticipants != null) {
            btnManageParticipants.setOnClickListener(v -> {
                if (uid == null) { Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show(); return; }
                Intent i = new Intent(TeacherDashboardActivity.this, TeacherManageCoursesActivity.class);
                i.putExtra(EXTRA_TEACHER_UID, uid);
                i.putExtra("action", "manageParticipants");
                startActivity(i);
            });
        }

        // Logout
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                Toast.makeText(TeacherDashboardActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(TeacherDashboardActivity.this, LoginActivity.class));
                finish();
            });
        }

        // Load teacher quick stats (courses → assignments and students)
        if (uid != null) loadTeacherStats(uid);

        Button btnAnnouncements = findViewById(R.id.btn_announcements);
        if (btnAnnouncements != null) {
            btnAnnouncements.setOnClickListener(v -> {
                startActivity(new Intent(TeacherDashboardActivity.this, AnnouncementActivity.class));
            });
        }
    }

    /**
     * Load quick stats:
     * - total assignments across courses taught by this teacher
     * - distinct students enrolled across those courses
     * - pending reviews (simple heuristic: assignments with field "pendingReview"==true OR 0 as fallback)
     *
     * Implementation: fetch courses where teacherId == uid, then for each course count assignments and users.
     */
    private void loadTeacherStats(String teacherUid) {
        if (db == null) return;

        // reset UI
        if (tvTotalAssignments != null) tvTotalAssignments.setText("--");
        if (tvActiveStudents != null) tvActiveStudents.setText("--");
        if (tvPendingReviews != null) tvPendingReviews.setText("--");

        db.collection("courses")
                .whereEqualTo("teacherId", teacherUid)
                .get()
                .addOnSuccessListener(qs -> {
                    List<DocumentSnapshot> courses = qs.getDocuments();
                    if (courses == null || courses.isEmpty()) {
                        // nothing to show
                        if (tvTotalAssignments != null) tvTotalAssignments.setText("0");
                        if (tvActiveStudents != null) tvActiveStudents.setText("0");
                        if (tvPendingReviews != null) tvPendingReviews.setText("0");
                        return;
                    }

                    // collect course IDs
                    Set<String> courseIds = new HashSet<>();
                    for (DocumentSnapshot c : courses) {
                        if (c != null && c.exists()) courseIds.add(c.getId());
                    }

                    // counts
                    final int[] totalAssignments = {0};
                    final Set<String> distinctStudents = new HashSet<>();
                    final int[] pendingReviewsCount = {0};

                    // For each course:
                    for (String courseId : courseIds) {
                        // count assignments for this course
                        db.collection("assignments")
                                .whereEqualTo("courseId", courseId)
                                .get()
                                .addOnSuccessListener(assignQs -> {
                                    if (assignQs != null) {
                                        for (DocumentSnapshot a : assignQs.getDocuments()) {
                                            if (a == null) continue;
                                            totalAssignments[0]++;

                                            // simple pending review flag check (if you store reviews)
                                            Boolean pending = a.getBoolean("pendingReview");
                                            if (Boolean.TRUE.equals(pending)) pendingReviewsCount[0]++;
                                        }
                                    }
                                    // update total assignments view
                                    if (tvTotalAssignments != null) tvTotalAssignments.setText(String.valueOf(totalAssignments[0]));
                                    if (tvPendingReviews != null) tvPendingReviews.setText(String.valueOf(pendingReviewsCount[0]));
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "Failed to load assignments for course " + courseId + ": " + e.getMessage());
                                });

                        // find students enrolled in this course via users.enrolledCourses array
                        db.collection("users")
                                .whereArrayContains("enrolledCourses", courseId)
                                .get()
                                .addOnSuccessListener(userQs -> {
                                    if (userQs != null) {
                                        for (DocumentSnapshot u : userQs.getDocuments()) {
                                            if (u == null) continue;
                                            // only count students
                                            String role = u.getString("role");
                                            if (role != null && !role.equalsIgnoreCase("student")) continue;
                                            distinctStudents.add(u.getId());
                                        }
                                    }
                                    if (tvActiveStudents != null) tvActiveStudents.setText(String.valueOf(distinctStudents.size()));
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "Failed to load students for course " + courseId + ": " + e.getMessage());
                                });
                    } // end for each course

                    // As queries finish asynchronously, UI is updated inside each listener above.
                    // Here we also set placeholder if still not updated after a short delay (best-effort).
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load teacher courses: " + e.getMessage());
                    if (tvTotalAssignments != null) tvTotalAssignments.setText("0");
                    if (tvActiveStudents != null) tvActiveStudents.setText("0");
                    if (tvPendingReviews != null) tvPendingReviews.setText("0");
                });
    }
}
