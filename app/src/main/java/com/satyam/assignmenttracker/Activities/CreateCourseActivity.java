package com.satyam.assignmenttracker.Activities;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin activity to create a course and assign a teacher to it.
 *
 * Usage:
 *  - Enter course title, code, description.
 *  - Enter teacher email -> Tap "Find Teacher".
 *  - If teacher is found, tap "Create Course" to create and link.
 *
 * Firestore expectations:
 *  - users collection has documents keyed by uid with fields:
 *      email: string
 *      role: "teacher" | "student" ...
 *
 *  - courses collection will be created with fields:
 *      title, code, description, teacherId, createdAt
 */
public class CreateCourseActivity extends AppCompatActivity {

    EditText etTitle, etCode, etDesc, etTeacherEmail;
    Button btnFindTeacher, btnCreateCourse;
    TextView tvTeacherFound;

    FirebaseFirestore db;
    ProgressDialog progress;

    // when teacher is found, we store uid and display name
    String foundTeacherUid = null;
    String foundTeacherDisplay = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_course);

        etTitle = findViewById(R.id.et_course_title);
        etCode = findViewById(R.id.et_course_code);
        etDesc = findViewById(R.id.et_course_desc);
        etTeacherEmail = findViewById(R.id.et_teacher_email);

        btnFindTeacher = findViewById(R.id.btn_find_teacher);
        btnCreateCourse = findViewById(R.id.btn_create_course);
        tvTeacherFound = findViewById(R.id.tv_teacher_found);

        db = FirebaseFirestore.getInstance();

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Please wait...");

        btnFindTeacher.setOnClickListener(v -> findTeacherByEmail());
        btnCreateCourse.setOnClickListener(v -> createCourse());
    }

    private void findTeacherByEmail() {
        String email = etTeacherEmail.getText() != null ? etTeacherEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            etTeacherEmail.setError("Teacher email required");
            etTeacherEmail.requestFocus();
            return;
        }

        progress.show();
        // query users collection for document with matching email
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    progress.dismiss();
                    if (qs.isEmpty()) {
                        foundTeacherUid = null;
                        foundTeacherDisplay = null;
                        tvTeacherFound.setText("Teacher not found. Make sure the teacher has a user account.");
                        Toast.makeText(this, "No user with that email", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DocumentSnapshot d = qs.getDocuments().get(0);
                    String role = d.getString("role");
                    if (role == null || !role.equalsIgnoreCase("teacher")) {
                        // found user but not a teacher role
                        foundTeacherUid = null;
                        foundTeacherDisplay = null;
                        tvTeacherFound.setText("User found but is not a teacher (role="+role+")");
                        Toast.makeText(this, "User found but not a teacher", Toast.LENGTH_LONG).show();
                        return;
                    }
                    foundTeacherUid = d.getId();
                    String name = d.getString("displayName");
                    String emailFound = d.getString("email");
                    foundTeacherDisplay = (name != null && !name.trim().isEmpty()) ? name : emailFound;
                    tvTeacherFound.setText("Found teacher: " + foundTeacherDisplay + " (uid: " + foundTeacherUid + ")");
                    Toast.makeText(this, "Teacher found: " + foundTeacherDisplay, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Search failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void createCourse() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String code = etCode.getText() != null ? etCode.getText().toString().trim() : "";
        String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title required");
            etTitle.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(code)) {
            etCode.setError("Code required");
            etCode.requestFocus();
            return;
        }
        if (foundTeacherUid == null) {
            Toast.makeText(this, "Find and select a teacher before creating the course", Toast.LENGTH_LONG).show();
            return;
        }

        progress.show();
        btnCreateCourse.setEnabled(false);

        long now = System.currentTimeMillis();
        Map<String, Object> course = new HashMap<>();
        course.put("title", title);
        course.put("code", code);
        course.put("description", desc);
        course.put("teacherId", foundTeacherUid);
        course.put("createdAt", now);

        db.collection("courses")
                .add(course)
                .addOnSuccessListener(docRef -> {
                    String courseId = docRef.getId();
                    // add courseId into teacher user doc under teacherCourses array
                    db.collection("users")
                            .document(foundTeacherUid)
                            .update("teacherCourses", FieldValue.arrayUnion(courseId))
                            .addOnSuccessListener(a -> {
                                progress.dismiss();
                                btnCreateCourse.setEnabled(true);
                                Toast.makeText(this, "Course created and teacher assigned", Toast.LENGTH_LONG).show();
                                // clear fields for next create
                                etTitle.setText("");
                                etCode.setText("");
                                etDesc.setText("");
                                etTeacherEmail.setText("");
                                tvTeacherFound.setText("");
                                foundTeacherUid = null;
                                foundTeacherDisplay = null;
                            })
                            .addOnFailureListener(e2 -> {
                                // course created but teacher user update failed; still inform user
                                progress.dismiss();
                                btnCreateCourse.setEnabled(true);
                                Toast.makeText(this, "Course created (id="+courseId+") but failed to update teacher doc: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    btnCreateCourse.setEnabled(true);
                    Toast.makeText(this, "Create failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
