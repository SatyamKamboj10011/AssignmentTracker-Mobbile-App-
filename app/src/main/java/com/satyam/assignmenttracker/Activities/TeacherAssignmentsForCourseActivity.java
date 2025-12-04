package com.satyam.assignmenttracker.Activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Assignment;
import com.satyam.assignmenttracker.Adapters.AssignmentAdapter;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.List;

public class TeacherAssignmentsForCourseActivity extends AppCompatActivity {

    private RecyclerView rv;
    private AssignmentAdapter adapter;
    private List<Assignment> list = new ArrayList<>();
    private FirebaseFirestore db;
    private ProgressDialog progress;
    private String courseId;
    private Button btnCreate, btnRefresh;
    private TextView tvCourseTitle;

    private FirebaseAuth mAuth;

    // ActivityResultLauncher for CreateAssignmentActivity
    private ActivityResultLauncher<Intent> createAssignmentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_assignments_for_course);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Please wait...");

        rv = findViewById(R.id.rvTeacherAssignments);
        btnCreate = findViewById(R.id.btnCreateAssignmentCourse);
        btnRefresh = findViewById(R.id.btnRefreshAssignmentsCourse);
        tvCourseTitle = findViewById(R.id.tvCourseTitleAssign);

        adapter = new AssignmentAdapter(this, list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        courseId = getIntent().getStringExtra("courseId");
        if (TextUtils.isEmpty(courseId)) {
            Toast.makeText(this, "Missing courseId", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadCourseMeta();
        loadAssignments();

        // Activity result launcher: refresh list when CreateAssignmentActivity returns OK
        createAssignmentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // A new or updated assignment was created; reload list
                        loadAssignments();
                    }
                }
        );

        // Launch CreateAssignmentActivity (pass courseId)
        btnCreate.setOnClickListener(v -> {
            Intent i = new Intent(TeacherAssignmentsForCourseActivity.this, CreateAssignmentActivity.class);
            i.putExtra("courseId", courseId);
            createAssignmentLauncher.launch(i);
        });

        btnRefresh.setOnClickListener(v -> loadAssignments());
    }

    private void loadCourseMeta() {
        db.collection("courses").document(courseId).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        tvCourseTitle.setText(doc.getString("title"));
                    } else tvCourseTitle.setText("Course");
                });
    }

    private void loadAssignments() {
        progress.show();
        list.clear();
        adapter.notifyDataSetChanged();

        db.collection("assignments")
                .whereEqualTo("courseId", courseId)
                .orderBy("dueDateMillis", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        try {
                            Assignment a = d.toObject(Assignment.class);
                            if (a == null) continue;
                            a.setId(d.getId());
                            list.add(a);
                        } catch (Exception ignored) {}
                    }
                    adapter.notifyDataSetChanged();
                    progress.dismiss();
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
