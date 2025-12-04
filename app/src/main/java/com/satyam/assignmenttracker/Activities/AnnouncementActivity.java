package com.satyam.assignmenttracker.Activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Announcement;
import com.satyam.assignmenttracker.Adapters.AnnouncementAdapter;
import com.satyam.assignmenttracker.models.Base;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class AnnouncementActivity extends Base {

    private RecyclerView rvAnnouncements;
    private TextView tvEmptyAnnouncements;
    private ImageButton btnBackAnnouncements;
    private FloatingActionButton fabAddAnnouncement;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private AnnouncementAdapter adapter;
    private final List<Announcement> announcementList = new ArrayList<>();

    private boolean canCreate = false; // teacher/admin only

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvAnnouncements = findViewById(R.id.rvAnnouncements);
        tvEmptyAnnouncements = findViewById(R.id.tvEmptyAnnouncements);
        btnBackAnnouncements = findViewById(R.id.btnBackAnnouncements);
        fabAddAnnouncement = findViewById(R.id.fabAddAnnouncement);

        if (btnBackAnnouncements != null) {
            btnBackAnnouncements.setOnClickListener(v -> onBackPressed());
        }

        // Adapter with click listener (show full announcement)
        adapter = new AnnouncementAdapter(a -> showAnnouncementDetails(a));
        rvAnnouncements.setLayoutManager(new LinearLayoutManager(this));
        rvAnnouncements.setAdapter(adapter);

        fabAddAnnouncement.setOnClickListener(v -> openCreateDialog());

        checkRoleThenLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh announcements
        loadAnnouncements();
    }

    // ------------- ROLE CHECK -------------

    private void checkRoleThenLoad() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            canCreate = false;
            fabAddAnnouncement.setVisibility(View.GONE);
            loadAnnouncements();
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String role = doc.getString("role");
                        canCreate = role != null &&
                                (role.equalsIgnoreCase("teacher")
                                        || role.equalsIgnoreCase("admin"));
                    } else {
                        canCreate = false;
                    }
                    fabAddAnnouncement.setVisibility(canCreate ? View.VISIBLE : View.GONE);
                    loadAnnouncements();
                })
                .addOnFailureListener(e -> {
                    canCreate = false;
                    fabAddAnnouncement.setVisibility(View.GONE);
                    loadAnnouncements();
                });
    }

    // ------------- LOAD ANNOUNCEMENTS -------------

    private void loadAnnouncements() {
        db.collection("announcements")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(q -> {
                    announcementList.clear();
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String id = d.getId();
                        String title = d.getString("title");
                        String message = d.getString("message");
                        String courseId = d.getString("courseId");
                        String courseTitle = d.getString("courseTitle");
                        String createdBy = d.getString("createdBy");
                        String creatorName = d.getString("creatorName");

                        Long createdAtMillis = null;
                        Object createdAtObj = d.get("createdAt");
                        if (createdAtObj instanceof Timestamp) {
                            Date date = ((Timestamp) createdAtObj).toDate();
                            createdAtMillis = date != null ? date.getTime() : null;
                        } else if (createdAtObj instanceof Number) {
                            createdAtMillis = ((Number) createdAtObj).longValue();
                        }

                        Announcement a = new Announcement(
                                id,
                                title,
                                message,
                                createdAtMillis,
                                courseId,
                                courseTitle,
                                createdBy,
                                creatorName
                        );
                        announcementList.add(a);
                    }

                    adapter.setItems(announcementList);
                    updateEmptyState();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateEmptyState();
                });
    }

    private void updateEmptyState() {
        if (announcementList.isEmpty()) {
            tvEmptyAnnouncements.setVisibility(View.VISIBLE);
            rvAnnouncements.setVisibility(View.GONE);
        } else {
            tvEmptyAnnouncements.setVisibility(View.GONE);
            rvAnnouncements.setVisibility(View.VISIBLE);
        }
    }

    // ------------- CREATE ANNOUNCEMENT (TEACHER/ADMIN) -------------

    private void openCreateDialog() {
        if (!canCreate) {
            Toast.makeText(this, "Only teachers/admins can create announcements", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dailog_create_announcement, null);
        EditText etTitle = view.findViewById(R.id.etAnnouncementTitle);
        EditText etMessage = view.findViewById(R.id.etAnnouncementMessage);

        new android.app.AlertDialog.Builder(this)
                .setTitle("New announcement")
                .setView(view)
                .setPositiveButton("Post", (dialog, which) -> {
                    String title = etTitle.getText() != null
                            ? etTitle.getText().toString().trim()
                            : "";
                    String message = etMessage.getText() != null
                            ? etMessage.getText().toString().trim()
                            : "";

                    if (TextUtils.isEmpty(title)) {
                        Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(message)) {
                        Toast.makeText(this, "Message required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    postAnnouncement(title, message);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void postAnnouncement(String title, String message) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String creatorName = null;
                    if (userDoc != null && userDoc.exists()) {
                        creatorName = userDoc.getString("displayName");
                        if (TextUtils.isEmpty(creatorName)) {
                            creatorName = userDoc.getString("email");
                        }
                    }
                    if (TextUtils.isEmpty(creatorName)) {
                        creatorName = "Teacher";
                    }

                    java.util.Map<String, Object> data = new HashMap<>();
                    data.put("title", title);
                    data.put("message", message);
                    data.put("createdBy", uid);
                    data.put("creatorName", creatorName);
                    data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                    data.put("courseId", null);
                    data.put("courseTitle", null);
                    data.put("roleVisibility", "all");

                    db.collection("announcements")
                            .add(data)
                            .addOnSuccessListener(docRef -> {
                                Toast.makeText(this, "Announcement posted", Toast.LENGTH_SHORT).show();
                                loadAnnouncements();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to post: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ------------- ITEM CLICK: show full details -------------

    private void showAnnouncementDetails(Announcement a) {
        if (a == null) return;

        StringBuilder msg = new StringBuilder();
        if (!TextUtils.isEmpty(a.getMessage())) {
            msg.append(a.getMessage());
        }

        if (a.getCreatedAt() != null) {
            String when = DateFormat.format("dd MMM yyyy, hh:mm a",
                    new Date(a.getCreatedAt())).toString();
            msg.append("\n\n").append("Posted on ").append(when);
        }

        if (!TextUtils.isEmpty(a.getCreatorName())) {
            msg.append("\n").append("By ").append(a.getCreatorName());
        }

        if (!TextUtils.isEmpty(a.getCourseTitle())) {
            msg.append("\n").append("Course: ").append(a.getCourseTitle());
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(!TextUtils.isEmpty(a.getTitle()) ? a.getTitle() : "Announcement")
                .setMessage(msg.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
