package com.satyam.assignmenttracker.Activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.*;
import com.satyam.assignmenttracker.R;
import com.satyam.assignmenttracker.models.User;

import java.util.*;
import java.util.function.Consumer;

/**
 * Admin: Manage users (list, create, edit, delete).
 * - Normalizes role to lowercase ("student","teacher","admin")
 * - Prevents deleting the last admin
 * - Properly updates Firestore on create/update/delete
 * - Keeps master list intact for filtering
 */
public class ManageUsersActivity extends AppCompatActivity {

    RecyclerView rv;
    UserAdapter adapter;
    List<User> masterList = new ArrayList<>(); // full list from Firestore
    List<User> displayList = new ArrayList<>(); // filtered list shown to adapter
    FirebaseFirestore db;
    Button btnRefresh, btnNewUser;
    EditText etFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);

        db = FirebaseFirestore.getInstance();
        rv = findViewById(R.id.rvUsers);
        btnRefresh = findViewById(R.id.btnRefreshUsers);
        btnNewUser = findViewById(R.id.btnNewUser);
        etFilter = findViewById(R.id.etUserSearch);

        adapter = new UserAdapter(this, displayList, new UserAdapter.Listener() {
            @Override public void onEdit(User user) { showEditDialog(user); }
            @Override public void onDelete(User user) { confirmAndDeleteUser(user); }
        });
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> loadUsers());
        btnNewUser.setOnClickListener(v -> showEditDialog(null));

        etFilter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c1, int c2) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filter(s.toString().trim()); }
        });

        loadUsers();
    }

    private void loadUsers() {
        db.collection("users").orderBy("email")
                .get()
                .addOnSuccessListener(qs -> {
                    masterList.clear();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        try {
                            User u = d.toObject(User.class);
                            if (u == null) u = new User();
                            u.setId(d.getId());
                            // populate missing fields defensively
                            if (u.getDisplayName() == null) u.setDisplayName(d.getString("displayName"));
                            if (u.getEmail() == null) u.setEmail(d.getString("email"));
                            if (u.getRole() == null) u.setRole(d.getString("role"));
                            masterList.add(u);
                        } catch (Exception ignored) {}
                    }
                    // copy to display list
                    displayList.clear();
                    displayList.addAll(masterList);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Loaded " + masterList.size() + " users", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load users: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void filter(String q) {
        if (TextUtils.isEmpty(q)) {
            displayList.clear();
            displayList.addAll(masterList);
            adapter.notifyDataSetChanged();
            return;
        }
        List<User> filtered = new ArrayList<>();
        String low = q.toLowerCase(Locale.ROOT);
        for (User u : masterList) {
            if ((u.getDisplayName() != null && u.getDisplayName().toLowerCase(Locale.ROOT).contains(low))
                    || (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(low))) {
                filtered.add(u);
            }
        }
        displayList.clear();
        displayList.addAll(filtered);
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(User existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_user, null);
        EditText etName = view.findViewById(R.id.et_user_name);
        EditText etEmail = view.findViewById(R.id.et_user_email);
        EditText etRole = view.findViewById(R.id.et_user_role);
        EditText etCourse = view.findViewById(R.id.et_assign_course);

        boolean isEdit = existing != null;
        if (isEdit) {
            etName.setText(existing.getDisplayName());
            etEmail.setText(existing.getEmail());
            etRole.setText(existing.getRole());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit user" : "Create user (Firestore doc only)")
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton(isEdit ? "Save" : "Create", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            pos.setOnClickListener(v -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                String role = etRole.getText() != null ? etRole.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
                String courseCode = etCourse.getText() != null ? etCourse.getText().toString().trim() : "";

                if (TextUtils.isEmpty(email)) { etEmail.setError("Email required"); etEmail.requestFocus(); return; }
                if (TextUtils.isEmpty(role)) { etRole.setError("Role required"); etRole.requestFocus(); return; }
                if (!role.equals("student") && !role.equals("teacher") && !role.equals("admin")) {
                    etRole.setError("Role must be student, teacher or admin"); etRole.requestFocus(); return;
                }

                if (isEdit) updateUser(existing, name, email, role, courseCode, dialog);
                else createUser(name, email, role, courseCode, dialog);
            });
        });

        dialog.show();
    }

    private void createUser(String name, String email, String role, String courseCode, AlertDialog dialog) {
        Map<String,Object> doc = new HashMap<>();
        doc.put("displayName", name);
        doc.put("email", email);
        doc.put("role", role.toLowerCase(Locale.ROOT));
        doc.put("createdAt", System.currentTimeMillis());
        if (role.equals("student")) doc.put("enrolledCourses", new ArrayList<String>());
        if (role.equals("teacher")) doc.put("teacherCourses", new ArrayList<String>());

        db.collection("users").add(doc)
                .addOnSuccessListener(ref -> {
                    if (!TextUtils.isEmpty(courseCode)) {
                        assignCourseToUserByCode(courseCode, ref.getId(), role, () -> {
                            dialog.dismiss();
                            loadUsers();
                            Toast.makeText(this, "User created and course assigned", Toast.LENGTH_SHORT).show();
                        }, err -> {
                            dialog.dismiss();
                            loadUsers();
                            Toast.makeText(this, "User created but course assign failed: " + err, Toast.LENGTH_LONG).show();
                        });
                    } else {
                        dialog.dismiss();
                        loadUsers();
                        Toast.makeText(this, "User created", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Create failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void updateUser(User existing, String name, String email, String role, String courseCode, AlertDialog dialog) {
        Map<String,Object> upd = new HashMap<>();
        upd.put("displayName", name);
        upd.put("email", email);
        upd.put("role", role.toLowerCase(Locale.ROOT));

        db.collection("users").document(existing.getId()).update(upd)
                .addOnSuccessListener(a -> {
                    if (!TextUtils.isEmpty(courseCode)) {
                        assignCourseToUserByCode(courseCode, existing.getId(), role, () -> {
                            dialog.dismiss();
                            loadUsers();
                            Toast.makeText(this, "User updated and course assigned", Toast.LENGTH_SHORT).show();
                        }, err -> {
                            dialog.dismiss();
                            loadUsers();
                            Toast.makeText(this, "Updated but course assignment failed: " + err, Toast.LENGTH_LONG).show();
                        });
                    } else {
                        dialog.dismiss();
                        loadUsers();
                        Toast.makeText(this, "User updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void confirmAndDeleteUser(User user) {
        // safety: don't allow deleting the currently signed-in admin or last admin
        new AlertDialog.Builder(this)
                .setTitle("Delete user")
                .setMessage("Delete user " + user.getEmail() + " ? This will remove the user's Firestore record (not their Auth account).")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> attemptDeleteUser(user))
                .show();
    }

    private void attemptDeleteUser(User user) {
        // if user has role admin -> ensure more than 1 admin exists
        if ("admin".equalsIgnoreCase(user.getRole())) {
            db.collection("users").whereEqualTo("role", "admin").get()
                    .addOnSuccessListener(qs -> {
                        if (qs.size() <= 1) {
                            Toast.makeText(this, "Cannot delete the last admin account.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        // safe to delete
                        performDeleteUser(user);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to verify admins: " + e.getMessage(), Toast.LENGTH_LONG).show());
        } else {
            performDeleteUser(user);
        }
    }

    private void performDeleteUser(User user) {
        db.collection("users").document(user.getId()).delete()
                .addOnSuccessListener(a -> {
                    // remove references in courses if teacher
                    if ("teacher".equalsIgnoreCase(user.getRole())) {
                        // remove teacherId from courses owned by this teacher
                        db.collection("courses").whereEqualTo("teacherId", user.getId()).get()
                                .addOnSuccessListener(qs -> {
                                    WriteBatch batch = db.batch();
                                    for (DocumentSnapshot d : qs.getDocuments()) {
                                        batch.update(d.getReference(), "teacherId", FieldValue.delete());
                                    }
                                    batch.commit().addOnSuccessListener(b -> {
                                        Toast.makeText(this, "User deleted and courses unassigned", Toast.LENGTH_SHORT).show();
                                        loadUsers();
                                    }).addOnFailureListener(e -> {
                                        Toast.makeText(this, "User deleted but failed to cleanup courses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        loadUsers();
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "User deleted but failed to find courses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    loadUsers();
                                });
                    } else {
                        Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show();
                        loadUsers();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /**
     * Assigns course to user by course code.
     * - finds course document with matching "code" field
     * - updates user doc: for students add to enrolledCourses arrayUnion(courseId)
     *                    for teachers add to teacherCourses arrayUnion(courseId) and set courses.teacherId = uid
     */
    private void assignCourseToUserByCode(String code, String userId, String role, Runnable onSuccess, Consumer<String> onError) {
        db.collection("courses").whereEqualTo("code", code).limit(1).get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) { onError.accept("Course not found"); return; }
                    DocumentSnapshot doc = qs.getDocuments().get(0);
                    String courseId = doc.getId();

                    if ("student".equalsIgnoreCase(role)) {
                        db.collection("users").document(userId)
                                .update("enrolledCourses", FieldValue.arrayUnion(courseId))
                                .addOnSuccessListener(a -> onSuccess.run())
                                .addOnFailureListener(e -> onError.accept(e.getMessage()));
                    } else if ("teacher".equalsIgnoreCase(role)) {
                        db.collection("courses").document(courseId)
                                .update("teacherId", userId)
                                .addOnSuccessListener(a1 -> {
                                    db.collection("users").document(userId)
                                            .update("teacherCourses", FieldValue.arrayUnion(courseId))
                                            .addOnSuccessListener(a2 -> onSuccess.run())
                                            .addOnFailureListener(e -> onError.accept(e.getMessage()));
                                })
                                .addOnFailureListener(e -> onError.accept(e.getMessage()));
                    } else {
                        onError.accept("Cannot assign course to role: " + role);
                    }
                })
                .addOnFailureListener(e -> onError.accept(e.getMessage()));
    }
}
