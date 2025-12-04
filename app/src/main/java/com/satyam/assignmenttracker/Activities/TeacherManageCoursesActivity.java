package com.satyam.assignmenttracker.Activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Course;
import com.satyam.assignmenttracker.Adapters.CourseAdapter;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.List;

/**
 * TeacherManageCoursesActivity
 *
 * Used by TeacherDashboardActivity for two flows:
 *  - action = "viewAssignments"     -> tap a course -> TeacherAssignmentsForCourseActivity
 *  - action = "manageParticipants"  -> tap a course -> ManageCourseParticipantsActivity
 *
 * This activity only lets the teacher pick one of their courses.
 */
public class TeacherManageCoursesActivity extends AppCompatActivity {

    RecyclerView rv;
    CourseAdapter adapter;
    List<Course> list = new ArrayList<>();

    FirebaseFirestore db;
    ProgressDialog progress;

    String teacherUid;
    String action; // "viewAssignments" or "manageParticipants"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_teacher_manage_courses);

        // Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main_teacher_manage_courses),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        rv = findViewById(R.id.rvCoursesTeacher);
        db = FirebaseFirestore.getInstance();

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Loading courses...");

        // Read extras from TeacherDashboardActivity
        teacherUid = getIntent().getStringExtra(TeacherDashboardActivity.EXTRA_TEACHER_UID);
        action = getIntent().getStringExtra("action");

        // Header text based on action
        TextView tvHeader = findViewById(R.id.tvTeacherManageHeader);
        if (tvHeader != null) {
            if ("manageParticipants".equalsIgnoreCase(action)) {
                tvHeader.setText("Select a course to manage students");
            } else if ("viewAssignments".equalsIgnoreCase(action)) {
                tvHeader.setText("Select a course to view assignments");
            } else {
                tvHeader.setText("Manage Courses");
            }
        }

        // Adapter: row click depends on action
        adapter = new CourseAdapter(this, list, new CourseAdapter.Listener() {
            @Override
            public void onItemClick(Course c) {
                if (c == null) return;

                if ("manageParticipants".equalsIgnoreCase(action)) {
                    Intent i = new Intent(TeacherManageCoursesActivity.this,
                            ManageCourseParticipantsActivity.class);
                    i.putExtra("courseId", c.getId());
                    startActivity(i);
                } else {
                    Intent i = new Intent(TeacherManageCoursesActivity.this,
                            TeacherAssignmentsForCourseActivity.class);
                    i.putExtra("courseId", c.getId());
                    startActivity(i);
                }
            }

            @Override
            public void onEdit(Course c) {
                Toast.makeText(TeacherManageCoursesActivity.this,
                        "Course editing is only available in the Admin Manage Courses screen.",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(Course c) {
                Toast.makeText(TeacherManageCoursesActivity.this,
                        "Course deletion is only available in the Admin Manage Courses screen.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        loadCoursesForTeacher();
    }

    private void loadCoursesForTeacher() {
        progress.show();

        Query q;
        if (teacherUid != null && !teacherUid.trim().isEmpty()) {
            q = db.collection("courses")
                    .whereEqualTo("teacherId", teacherUid)
                    .orderBy("createdAt", Query.Direction.ASCENDING);
        } else {
            q = db.collection("courses")
                    .orderBy("createdAt", Query.Direction.ASCENDING);
        }

        q.get()
                .addOnSuccessListener(qs -> {
                    list.clear();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        try {
                            Course c = d.toObject(Course.class);
                            if (c == null) continue;
                            c.setId(d.getId());
                            list.add(c);
                        } catch (Exception ignored) { }
                    }
                    adapter.notifyDataSetChanged();
                    progress.dismiss();

                    if (list.isEmpty()) {
                        Toast.makeText(this, "No courses found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this,
                            "Failed to load courses: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
