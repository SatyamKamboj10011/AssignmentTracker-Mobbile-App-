package com.satyam.assignmenttracker.Activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.R;
import com.satyam.assignmenttracker.Adapters.StudentAdapter;
import com.satyam.assignmenttracker.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ManageCourseParticipantsActivity
 * - Lists students enrolled in a course (users.enrolledCourses contains courseId)
 * - Allows removing a student from the course
 * - Allows adding a student by email
 */
public class ManageCourseParticipantsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private StudentAdapter adapter;
    private List<User> students = new ArrayList<>();
    private FirebaseFirestore db;
    private String courseId;

    private Button btnAddStudentByEmail;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_course_participants);

        db = FirebaseFirestore.getInstance();

        rv = findViewById(R.id.rvCourseParticipants);
        btnAddStudentByEmail = findViewById(R.id.btnAddStudentByEmail);
        progressBar = findViewById(R.id.progressParticipants);

        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new StudentAdapter(this, students, new StudentAdapter.Listener() {
            @Override
            public void onRemoveStudent(User student) {
                confirmAndRemoveStudent(student);
            }

            @Override
            public void onClickStudent(User student) {
                Toast.makeText(ManageCourseParticipantsActivity.this,
                        "Clicked: " + (student.getDisplayName() != null
                                ? student.getDisplayName()
                                : student.getEmail()),
                        Toast.LENGTH_SHORT).show();
            }
        });
        rv.setAdapter(adapter);

        courseId = getIntent() != null ? getIntent().getStringExtra("courseId") : null;
        if (courseId == null) {
            Toast.makeText(this, "Missing courseId", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (btnAddStudentByEmail != null) {
            btnAddStudentByEmail.setOnClickListener(v -> showAddStudentByEmailDialog());
        }

        loadParticipants();
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void loadParticipants() {
        setLoading(true);
        students.clear();
        adapter.notifyDataSetChanged();

        db.collection("users")
                .whereArrayContains("enrolledCourses", courseId)
                .get()
                .addOnSuccessListener(qs -> {
                    setLoading(false);

                    List<DocumentSnapshot> docs = qs.getDocuments();
                    if (docs.isEmpty()) {
                        Toast.makeText(this, "No students enrolled in this course", Toast.LENGTH_SHORT).show();
                        students.clear();
                        adapter.notifyDataSetChanged();
                        return;
                    }

                    List<User> tmp = new ArrayList<>();
                    for (DocumentSnapshot d : docs) {
                        try {
                            String role = d.getString("role");
                            if (role != null && !role.equalsIgnoreCase("student")) {
                                continue;
                            }

                            User u = d.toObject(User.class);
                            if (u == null) u = new User();
                            u.setId(d.getId());

                            if (u.getDisplayName() == null) u.setDisplayName(d.getString("displayName"));
                            if (u.getEmail() == null) u.setEmail(d.getString("email"));

                            tmp.add(u);
                        } catch (Exception ignored) { }
                    }

                    Collections.sort(tmp, new Comparator<User>() {
                        @Override
                        public int compare(User a, User b) {
                            String an = a.getDisplayName() != null ? a.getDisplayName() : a.getEmail();
                            String bn = b.getDisplayName() != null ? b.getDisplayName() : b.getEmail();
                            if (an == null) an = "";
                            if (bn == null) bn = "";
                            return an.compareToIgnoreCase(bn);
                        }
                    });

                    students.clear();
                    students.addAll(tmp);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to load participants: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void confirmAndRemoveStudent(User student) {
        new AlertDialog.Builder(this)
                .setTitle("Remove student")
                .setMessage("Remove " +
                        (student.getDisplayName() != null ? student.getDisplayName() : student.getEmail())
                        + " from this course?")
                .setPositiveButton("Remove", (dlg, which) -> removeStudentFromCourse(student))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeStudentFromCourse(User student) {
        if (student.getId() == null) {
            Toast.makeText(this, "Invalid student record", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(student.getId())
                .update("enrolledCourses", FieldValue.arrayRemove(courseId))
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Student removed", Toast.LENGTH_SHORT).show();
                    loadParticipants();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to remove: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // ------------------ ADD STUDENT BY EMAIL ------------------

    private void showAddStudentByEmailDialog() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_student_by_email, null);

        EditText etEmail = view.findViewById(R.id.et_student_email);

        new AlertDialog.Builder(this)
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dlg, which) -> {
                    String email = etEmail.getText() != null
                            ? etEmail.getText().toString().trim()
                            : "";

                    if (TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    addStudentByEmail(email);
                })
                .show();
    }

    private void addStudentByEmail(String email) {
        setLoading(true);

        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) {
                        setLoading(false);
                        Toast.makeText(this,
                                "No user found with that email",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    DocumentSnapshot doc = qs.getDocuments().get(0);
                    String uid = doc.getId();

                    db.collection("users").document(uid)
                            .update("enrolledCourses", FieldValue.arrayUnion(courseId))
                            .addOnSuccessListener(a -> {
                                setLoading(false);
                                Toast.makeText(this,
                                        "Student added to course",
                                        Toast.LENGTH_SHORT).show();
                                loadParticipants();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this,
                                        "Failed to enroll: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this,
                            "Lookup failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
