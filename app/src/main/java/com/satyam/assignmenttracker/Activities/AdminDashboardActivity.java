package com.satyam.assignmenttracker.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.models.Base;
import com.satyam.assignmenttracker.R;

public class AdminDashboardActivity extends Base {

    private static final String TAG = "AdminDashboardActivity";

    private TextView tvWelcome;
    private TextView tvRole;
    private LinearLayout manageusers;
    private Button btnManageInvites, btnManageCourses, btnCreateCourses, btnLogout;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // --- Init Firebase ---
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // --- Bind Views ---
        tvWelcome = findViewById(R.id.tv_welcome);
        tvRole = findViewById(R.id.tv_role);
        btnManageInvites = findViewById(R.id.btn_manage_invites);
        btnManageCourses = findViewById(R.id.btn_manage_courses);
        btnCreateCourses = findViewById(R.id.btn_create_courses);
        btnLogout = findViewById(R.id.btn_logout);
        manageusers = findViewById(R.id.manage_users);

        // Safety checks for null views (in case XML IDs don't match)
        if (tvWelcome == null || tvRole == null ||
                btnManageInvites == null || btnManageCourses == null ||
                btnCreateCourses == null || btnLogout == null ||
                manageusers == null) {

            Log.e(TAG, "One or more views are null. Check activity_admin_dashboard.xml IDs.");
            Toast.makeText(this,
                    "Dashboard layout error. Please check view IDs.",
                    Toast.LENGTH_LONG).show();
        }

        // --- Check auth state ---
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // Not signed in -> send to login
            Log.w(TAG, "User is null, redirecting to LoginActivity");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // --- Show basic user info ---
        if (tvWelcome != null) {
            tvWelcome.setText("Welcome,\n" + user.getEmail());
        }

        // --- Load role from Firestore ---
        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (tvRole == null) return;

                    if (doc != null && doc.exists()) {
                        String role = doc.getString("role");
                        if (role != null && !role.trim().isEmpty()) {
                            tvRole.setText("Role: " + role);
                        } else {
                            tvRole.setText("Role: (not set)");
                        }
                    } else {
                        tvRole.setText("Role: (not set)");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load user role", e);
                    if (tvRole != null) {
                        tvRole.setText("Role: (error)");
                    }
                });

        // --- Click listeners ---

        if (btnManageInvites != null) {
            btnManageInvites.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboardActivity.this, ManageInvitesActivity.class)));
        }

        if (manageusers != null) {
            manageusers.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboardActivity.this, ManageUsersActivity.class)));
        }

        if (btnManageCourses != null) {
            btnManageCourses.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboardActivity.this, ManageCoursesActivity.class)));
        }

        if (btnCreateCourses != null) {
            btnCreateCourses.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboardActivity.this, CreateCourseActivity.class)));
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                Toast.makeText(AdminDashboardActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(AdminDashboardActivity.this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            });


            Button btnAnnouncements = findViewById(R.id.btn_announcements);
            if (btnAnnouncements != null) {
                btnAnnouncements.setOnClickListener(v -> {
                    startActivity(new Intent(AdminDashboardActivity.this, AnnouncementActivity.class));
                });
            }
        }
    }
}
