package com.satyam.assignmenttracker.Activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Course;
import com.satyam.assignmenttracker.Adapters.CourseAdapter;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Manage Courses activity: list, refresh, edit and delete courses.
 * Includes inline dialog for creating / editing a course (select teacher from list).
 */
public class ManageCoursesActivity extends AppCompatActivity {

    RecyclerView rv;
    CourseAdapter adapter;
    List<Course> list = new ArrayList<>();
    FirebaseFirestore db;

    ImageButton btnRefresh;              // matches ImageButton in XML
    FloatingActionButton btnNewCourse;   // matches FAB in XML

    ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_courses);

        db = FirebaseFirestore.getInstance();
        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Please wait...");

        rv = findViewById(R.id.rvCourses);
        btnRefresh = findViewById(R.id.btnRefreshCourses);
        btnNewCourse = findViewById(R.id.btnNewCourse);

        adapter = new CourseAdapter(this, list, new CourseAdapter.Listener() {
            @Override
            public void onItemClick(Course course) {
                // default row tap -> open edit dialog for admin
                showEditDialog(course);
            }

            @Override
            public void onEdit(Course course) {
                showEditDialog(course);
            }

            @Override
            public void onDelete(Course course) {
                deleteCourse(course);
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> loadCourses());
        btnNewCourse.setOnClickListener(v -> showEditDialog(null));

        loadCourses();
    }

    private void loadCourses() {
        progress.show();
        db.collection("courses")
                .orderBy("createdAt")
                .get()
                .addOnSuccessListener(qs -> {
                    list.clear();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        try {
                            Course c = d.toObject(Course.class);
                            if (c == null) continue;
                            c.setId(d.getId());
                            list.add(c);
                        } catch (Exception e) {
                            // ignore single bad doc
                        }
                    }
                    adapter.notifyDataSetChanged();
                    progress.dismiss();
                    if (list.isEmpty()) {
                        Toast.makeText(this, "No courses found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Failed to load courses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Show create / edit dialog.
     * The teacher field is a selector: tapping the field opens a list of teachers.
     */
    private void showEditDialog(Course existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_course, null);
        EditText etTitle = view.findViewById(R.id.et_course_title);
        EditText etCode = view.findViewById(R.id.et_course_code);
        EditText etDesc = view.findViewById(R.id.et_course_desc);
        EditText etTeacherEmail = view.findViewById(R.id.et_teacher_email);

        // Selected teacher uid (null = none)
        final String[] selectedTeacherUid = {null};

        boolean isEdit = existing != null;
        if (isEdit) {
            etTitle.setText(existing.getTitle());
            etCode.setText(existing.getCode());
            etDesc.setText(existing.getDescription());
            // existing.getTeacherId() is uid; display human text by fetching user if present
            if (existing.getTeacherId() != null && !existing.getTeacherId().isEmpty()) {
                selectedTeacherUid[0] = existing.getTeacherId();
                // fetch user's displayName/email to show
                db.collection("users").document(selectedTeacherUid[0]).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String display = doc.getString("displayName");
                                String email = doc.getString("email");
                                etTeacherEmail.setText(display != null ? (display + " — " + email) : email);
                            } else {
                                etTeacherEmail.setText(selectedTeacherUid[0]);
                            }
                        })
                        .addOnFailureListener(e -> etTeacherEmail.setText(selectedTeacherUid[0]));
            } else {
                etTeacherEmail.setText("");
            }
        } else {
            etTeacherEmail.setText("");
        }

        // Make teacher field non-editable and act as a selector
        etTeacherEmail.setFocusable(false);
        etTeacherEmail.setClickable(true);
        etTeacherEmail.setOnClickListener(v -> {
            // open teacher picker dialog
            showTeacherPicker(selectedTeacherUid, etTeacherEmail);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Course" : "Create Course")
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton(isEdit ? "Save" : "Create", null) // override below
                .create();

        dialog.setOnShowListener(dlg -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            pos.setOnClickListener(v -> {
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

                if (isEdit) {
                    updateCourse(existing, title, code, desc, selectedTeacherUid[0], dialog);
                } else {
                    createCourse(title, code, desc, selectedTeacherUid[0], dialog);
                }
            });
        });

        dialog.show();
    }

    private void createCourse(String title, String code, String desc, String teacherUid, AlertDialog dialog) {
        progress.show();
        long now = System.currentTimeMillis();
        Map<String, Object> course = new HashMap<>();
        course.put("title", title);
        course.put("code", code);
        course.put("description", desc);
        course.put("teacherId", null);
        course.put("createdAt", now);

        db.collection("courses").add(course)
                .addOnSuccessListener(docRef -> {
                    String courseId = docRef.getId();
                    if (!TextUtils.isEmpty(teacherUid)) {
                        // assign by uid
                        assignTeacherByUidToCourse(teacherUid, courseId, () -> {
                            progress.dismiss();
                            dialog.dismiss();
                            loadCourses();
                            Toast.makeText(this, "Course created and teacher assigned", Toast.LENGTH_LONG).show();
                        }, err -> {
                            progress.dismiss();
                            dialog.dismiss();
                            loadCourses();
                            Toast.makeText(this, "Course created but teacher assignment failed: " + err, Toast.LENGTH_LONG).show();
                        });
                    } else {
                        progress.dismiss();
                        dialog.dismiss();
                        loadCourses();
                        Toast.makeText(this, "Course created", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Create failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateCourse(Course existing, String title, String code, String desc, String teacherUid, AlertDialog dialog) {
        progress.show();
        Map<String, Object> upd = new HashMap<>();
        upd.put("title", title);
        upd.put("code", code);
        upd.put("description", desc);

        db.collection("courses").document(existing.getId())
                .update(upd)
                .addOnSuccessListener(a -> {
                    // handle teacher reassignment
                    if (!TextUtils.isEmpty(teacherUid)) {
                        // if same teacher as before, nothing extra to do
                        if (teacherUid.equals(existing.getTeacherId())) {
                            progress.dismiss();
                            dialog.dismiss();
                            loadCourses();
                            Toast.makeText(this, "Course updated", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // else assign new teacher and remove course from old teacher
                        reassignTeacherForCourse(teacherUid, existing.getId(), existing.getTeacherId(), () -> {
                            progress.dismiss();
                            dialog.dismiss();
                            loadCourses();
                            Toast.makeText(this, "Course updated and teacher reassigned", Toast.LENGTH_LONG).show();
                        }, err -> {
                            progress.dismiss();
                            dialog.dismiss();
                            loadCourses();
                            Toast.makeText(this, "Updated but teacher assign failed: " + err, Toast.LENGTH_LONG).show();
                        });
                    } else {
                        // no teacher selected -> remove teacher if existed
                        if (existing.getTeacherId() != null && !existing.getTeacherId().isEmpty()) {
                            // clear teacherId and remove course from old teacher's array
                            db.collection("courses").document(existing.getId())
                                    .update("teacherId", null)
                                    .addOnSuccessListener(x -> {
                                        db.collection("users").document(existing.getTeacherId())
                                                .update("teacherCourses", FieldValue.arrayRemove(existing.getId()))
                                                .addOnSuccessListener(xx -> {
                                                    progress.dismiss();
                                                    dialog.dismiss();
                                                    loadCourses();
                                                    Toast.makeText(this, "Course updated and previous teacher unassigned", Toast.LENGTH_LONG).show();
                                                })
                                                .addOnFailureListener(e -> {
                                                    progress.dismiss();
                                                    dialog.dismiss();
                                                    loadCourses();
                                                    Toast.makeText(this, "Updated but failed to remove course from previous teacher: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        progress.dismiss();
                                        dialog.dismiss();
                                        loadCourses();
                                        Toast.makeText(this, "Updated but failed to clear teacherId: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                        } else {
                            progress.dismiss();
                            dialog.dismiss();
                            loadCourses();
                            Toast.makeText(this, "Course updated", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteCourse(Course course) {
        progress.show();
        db.collection("courses").document(course.getId())
                .delete()
                .addOnSuccessListener(a -> {
                    if (course.getTeacherId() != null && !course.getTeacherId().isEmpty()) {
                        db.collection("users").document(course.getTeacherId())
                                .update("teacherCourses", FieldValue.arrayRemove(course.getId()))
                                .addOnSuccessListener(b -> {
                                    progress.dismiss();
                                    loadCourses();
                                    Toast.makeText(this, "Course deleted", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    progress.dismiss();
                                    loadCourses();
                                    Toast.makeText(this, "Deleted course but failed to update teacher doc: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    } else {
                        progress.dismiss();
                        loadCourses();
                        Toast.makeText(this, "Course deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // callback interfaces
    private interface SuccessCallback {
        void onSuccess();
    }

    private interface ErrorCallback {
        void onError(String err);
    }

    /**
     * Open a dialog to pick a teacher from users where role == "teacher".
     * selectedTeacherUid[0] will be filled with the uid; etTeacherEmail shows displayName — email.
     */
    private void showTeacherPicker(final String[] selectedTeacherUid, final EditText etTeacherEmail) {
        progress.show();
        db.collection("users")
                .whereEqualTo("role", "teacher")
                .orderBy("email", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    progress.dismiss();
                    if (qs.isEmpty()) {
                        Toast.makeText(this, "No teachers found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final List<String> labels = new ArrayList<>();
                    final List<String> uids = new ArrayList<>();
                    int preselect = -1;
                    for (int i = 0; i < qs.size(); i++) {
                        DocumentSnapshot d = qs.getDocuments().get(i);
                        String uid = d.getId();
                        String display = d.getString("displayName");
                        String email = d.getString("email");
                        String label = (display != null && !display.isEmpty())
                                ? (display + " — " + email)
                                : (email != null ? email : uid);
                        labels.add(label);
                        uids.add(uid);
                        if (selectedTeacherUid[0] != null && selectedTeacherUid[0].equals(uid)) preselect = i;
                    }

                    CharSequence[] items = labels.toArray(new CharSequence[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("Select teacher")
                            .setSingleChoiceItems(items, preselect, (dialog, which) -> {
                                // on item click: set selection immediately and dismiss
                                selectedTeacherUid[0] = uids.get(which);
                                etTeacherEmail.setText(labels.get(which));
                                dialog.dismiss();
                            })
                            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                            .show();

                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Failed to load teachers: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Assign teacher by UID to a course (used on creation).
     * Updates courses/<courseId>.teacherId and adds courseId to users/<uid>.teacherCourses.
     */
    private void assignTeacherByUidToCourse(String teacherUid, String courseId, SuccessCallback onSuccess, ErrorCallback onError) {
        db.collection("courses").document(courseId)
                .update("teacherId", teacherUid)
                .addOnSuccessListener(a -> {
                    db.collection("users").document(teacherUid)
                            .update("teacherCourses", FieldValue.arrayUnion(courseId))
                            .addOnSuccessListener(b -> onSuccess.onSuccess())
                            .addOnFailureListener(e -> onError.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> onError.onError(e.getMessage()));
    }

    /**
     * Reassign teacher: set new teacherUid on course, add course to new teacherCourses,
     * and remove course from oldTeacherUid.teacherCourses (if oldTeacherUid non-null).
     */
    private void reassignTeacherForCourse(String newTeacherUid, String courseId, String oldTeacherUid, SuccessCallback onSuccess, ErrorCallback onError) {
        // update course doc to point to new teacher
        db.collection("courses").document(courseId)
                .update("teacherId", newTeacherUid)
                .addOnSuccessListener(a -> {
                    // add course to new teacher's array
                    db.collection("users").document(newTeacherUid)
                            .update("teacherCourses", FieldValue.arrayUnion(courseId))
                            .addOnSuccessListener(b -> {
                                // remove from old teacher if applicable
                                if (oldTeacherUid != null && !oldTeacherUid.isEmpty()) {
                                    db.collection("users").document(oldTeacherUid)
                                            .update("teacherCourses", FieldValue.arrayRemove(courseId))
                                            .addOnSuccessListener(c -> onSuccess.onSuccess())
                                            .addOnFailureListener(e -> onError.onError(e.getMessage()));
                                } else {
                                    onSuccess.onSuccess();
                                }
                            })
                            .addOnFailureListener(e -> onError.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> onError.onError(e.getMessage()));
    }
}
