package com.satyam.assignmenttracker.Activities;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Base;
import com.satyam.assignmenttracker.Adapters.CloudinaryUploader;
import com.satyam.assignmenttracker.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CreateAssignmentActivity extends Base {

    private static final String TAG = "CreateAssignment";

    EditText etTitle, etDescription, etDueDate;
    TextView tvFileName, tvPickedStudents;
    CheckBox chkAssignAll;
    Button btnCreate, btnPickFile, btnPickStudents, btnManageGroups;

    final List<String> pickedStudentUids = new ArrayList<>();
    boolean assignToAllStudents = true;

    // Groups for this assignment
    static class AssignmentGroup {
        String groupId;
        String groupName;
        List<String> memberUids = new ArrayList<>();
    }

    final List<AssignmentGroup> groupList = new ArrayList<>();

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    Calendar dueCalendar = Calendar.getInstance();
    SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    ProgressDialog progress;

    Uri pickedFileUri = null;
    String pickedFileName = null;
    long pickedFileSize = -1;

    Executor executor = Executors.newSingleThreadExecutor();

    ActivityResultLauncher<String> pickFileLauncher;

    String courseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_assignment);

        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        etDueDate = findViewById(R.id.et_due_date);
        tvFileName = findViewById(R.id.tv_file_name);
        chkAssignAll = findViewById(R.id.chk_assign_all);
        btnCreate = findViewById(R.id.btn_create);
        btnPickFile = findViewById(R.id.btn_pick_file);
        btnPickStudents = findViewById(R.id.btn_pick_students);
        tvPickedStudents = findViewById(R.id.tv_picked_students);
        btnManageGroups = findViewById(R.id.btn_manage_groups);

        courseId = getIntent().getStringExtra("courseId");

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Please wait...");

        // Date picker
        etDueDate.setFocusable(false);
        etDueDate.setClickable(true);
        etDueDate.setOnClickListener(v -> showDatePicker());

        // File picker
        pickFileLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        pickedFileUri = uri;
                        pickedFileName = getFileName(uri);
                        pickedFileSize = getFileSize(uri);
                        tvFileName.setText("Selected: " + pickedFileName);
                        Log.d(TAG, "Picked file: " + pickedFileName + " uri=" + uri);
                    }
                });

        btnPickFile.setOnClickListener(v -> pickFileLauncher.launch("*/*"));

        // Simple student multi-select (no groups) – still available
        if (btnPickStudents != null && tvPickedStudents != null) {
            tvPickedStudents.setText("(all students)");
            btnPickStudents.setOnClickListener(v -> {
                if (TextUtils.isEmpty(courseId)) {
                    Toast.makeText(CreateAssignmentActivity.this, "No course context to pick students", Toast.LENGTH_LONG).show();
                    return;
                }
                openStudentMultiSelect(selected -> {
                    pickedStudentUids.clear();
                    pickedStudentUids.addAll(selected);
                    assignToAllStudents = selected.isEmpty();
                    tvPickedStudents.setText(selected.isEmpty() ? "(all students)" : (selected.size() + " selected"));
                });
            });
        }

        // Manage groups (named groups with members)
        if (btnManageGroups != null) {
            btnManageGroups.setOnClickListener(v -> {
                if (TextUtils.isEmpty(courseId)) {
                    Toast.makeText(this, "Need course to manage groups", Toast.LENGTH_LONG).show();
                    return;
                }
                openGroupCreatorDialog();
            });
        }

        btnCreate.setOnClickListener(v -> createAssignment());
    }

    private void showDatePicker() {
        int year = dueCalendar.get(Calendar.YEAR);
        int month = dueCalendar.get(Calendar.MONTH);
        int day = dueCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dp = new DatePickerDialog(CreateAssignmentActivity.this,
                (DatePicker datePicker, int y, int m, int d) -> {
                    dueCalendar.set(y, m, d);
                    etDueDate.setText(displayDateFormat.format(dueCalendar.getTime()));
                }, year, month, day);
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.show();
    }

    private String getFileName(Uri uri) {
        if (uri == null) return null;
        String result = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        if (result == null) {
            String last = uri.getLastPathSegment();
            result = last != null ? last : "attachment";
        }
        return result;
    }

    private long getFileSize(Uri uri) {
        if (uri == null) return -1;
        long size = -1;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) size = cursor.getLong(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return size;
    }

    private void createAssignment() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String desc = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String dueText = etDueDate.getText() != null ? etDueDate.getText().toString().trim() : "";
        boolean assignToAllCheckbox = (chkAssignAll != null) && chkAssignAll.isChecked();

        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title required");
            etTitle.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(desc)) {
            etDescription.setError("Description required");
            etDescription.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(dueText)) {
            etDueDate.setError("Due date required");
            etDueDate.requestFocus();
            return;
        }
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "You must sign in to create assignments", Toast.LENGTH_LONG).show();
            return;
        }

        setUiBusy(true);

        long dueMillis = dueCalendar.getTimeInMillis();
        long now = System.currentTimeMillis();
        String uid = mAuth.getCurrentUser().getUid();
        String email = mAuth.getCurrentUser().getEmail();
        if (email == null) email = "";

        Map<String, Object> assignment = new HashMap<>();
        assignment.put("title", title);
        assignment.put("description", desc);
        assignment.put("dueDate", dueText);
        assignment.put("dueDateMillis", dueMillis);
        assignment.put("createdBy", uid);
        assignment.put("createdByEmail", email);
        assignment.put("timestamp", now);
        if (!TextUtils.isEmpty(courseId)) assignment.put("courseId", courseId);

        // GROUP / ASSIGN-TO LOGIC
        boolean hasGroups = !groupList.isEmpty();
        List<String> assignedToList = new ArrayList<>();

        if (hasGroups) {
            for (AssignmentGroup g : groupList) {
                if (g.memberUids != null) {
                    for (String su : g.memberUids) {
                        if (!assignedToList.contains(su)) {
                            assignedToList.add(su);
                        }
                    }
                }
            }
            assignment.put("assignToAll", false);
            assignment.put("assignedTo", assignedToList);

            List<Map<String, Object>> groupsArr = new ArrayList<>();
            for (AssignmentGroup g : groupList) {
                Map<String, Object> gm = new HashMap<>();
                gm.put("groupId", g.groupId);
                gm.put("groupName", g.groupName);
                gm.put("memberUids", g.memberUids);
                groupsArr.add(gm);
            }
            assignment.put("groups", groupsArr);

        } else {
            boolean finalAssignToAll = assignToAllCheckbox || assignToAllStudents;
            assignment.put("assignToAll", finalAssignToAll);
            if (!finalAssignToAll && !pickedStudentUids.isEmpty()) {
                assignment.put("assignedTo", pickedStudentUids);
            }
        }

        // Create Firestore doc
        if (pickedFileUri == null) {
            db.collection("assignments").add(assignment)
                    .addOnSuccessListener(docRef -> {
                        setUiBusy(false);
                        Toast.makeText(CreateAssignmentActivity.this, "Assignment created", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setUiBusy(false);
                        Toast.makeText(CreateAssignmentActivity.this, "Create failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "create assignment failed", e);
                    });
            return;
        }

        db.collection("assignments").add(assignment)
                .addOnSuccessListener(docRef -> {
                    docRef.update("uploading", true)
                            .addOnCompleteListener(ignore ->
                                    uploadFileToCloudinaryAndSave(docRef, pickedFileUri, pickedFileName, pickedFileSize)
                            );
                })
                .addOnFailureListener(e -> {
                    setUiBusy(false);
                    Toast.makeText(CreateAssignmentActivity.this, "Create failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "create assignment + file doc failed", e);
                });
    }

    private void uploadFileToCloudinaryAndSave(final DocumentReference docRef,
                                               final Uri fileUri,
                                               final String fileName,
                                               final long fileSize) {
        executor.execute(() -> {
            try {
                String secureUrl = CloudinaryUploader.uploadUriStreaming(getContentResolver(), fileUri, fileName);
                Map<String, Object> upd = new HashMap<>();
                upd.put("fileUrl", secureUrl);
                upd.put("fileName", fileName);
                upd.put("fileSize", fileSize >= 0 ? (fileSize + " bytes") : "unknown");
                upd.put("uploading", false);

                docRef.update(upd)
                        .addOnSuccessListener(a -> runOnUiThread(() -> {
                            setUiBusy(false);
                            Toast.makeText(CreateAssignmentActivity.this, "Assignment created and file uploaded", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        }))
                        .addOnFailureListener(e -> {
                            try {
                                docRef.update("uploading", false, "uploadError", "update_failed");
                            } catch (Exception ignored) {}
                            runOnUiThread(() -> {
                                setUiBusy(false);
                                Toast.makeText(CreateAssignmentActivity.this, "Uploaded but failed to save link", Toast.LENGTH_LONG).show();
                            });
                        });
            } catch (final Exception e) {
                try {
                    docRef.update("uploading", false, "uploadError", e.getMessage());
                } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    setUiBusy(false);
                    Toast.makeText(CreateAssignmentActivity.this, "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ------- simple multi-select (no groups) -------

    private interface StudentPickCallback { void onPicked(List<String> uids); }

    private void openStudentMultiSelect(StudentPickCallback cb) {
        progress.show();
        db.collection("users")
                .whereArrayContains("enrolledCourses", courseId)
                .orderBy("email", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    progress.dismiss();
                    List<com.google.firebase.firestore.DocumentSnapshot> docs = qs.getDocuments();
                    if (docs.isEmpty()) {
                        Toast.makeText(this, "No students found", Toast.LENGTH_SHORT).show();
                        cb.onPicked(new ArrayList<>());
                        return;
                    }
                    final String[] emails = new String[docs.size()];
                    final String[] uids = new String[docs.size()];
                    final boolean[] checked = new boolean[docs.size()];
                    for (int i = 0; i < docs.size(); i++) {
                        com.google.firebase.firestore.DocumentSnapshot d = docs.get(i);
                        String display = d.getString("displayName") != null
                                ? d.getString("displayName") + " — " + d.getString("email")
                                : d.getString("email");
                        emails[i] = display != null ? display : d.getString("email");
                        uids[i] = d.getId();
                        checked[i] = false;
                    }
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Select students")
                            .setMultiChoiceItems(emails, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                            .setPositiveButton("OK", (dialog, which) -> {
                                List<String> picked = new ArrayList<>();
                                for (int i = 0; i < uids.length; i++) if (checked[i]) picked.add(uids[i]);
                                cb.onPicked(picked);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Failed to load students: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    cb.onPicked(new ArrayList<>());
                });
    }

    // ------- Group creator dialog -------

    private void openGroupCreatorDialog() {
        progress.show();
        db.collection("users")
                .whereArrayContains("enrolledCourses", courseId)
                .orderBy("email", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    progress.dismiss();
                    List<com.google.firebase.firestore.DocumentSnapshot> docs = qs.getDocuments();
                    if (docs.isEmpty()) {
                        Toast.makeText(this, "No students found for course", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final String[] labels = new String[docs.size()];
                    final String[] uids = new String[docs.size()];
                    final boolean[] checked = new boolean[docs.size()];
                    for (int i = 0; i < docs.size(); i++) {
                        com.google.firebase.firestore.DocumentSnapshot d = docs.get(i);
                        String display = d.getString("displayName");
                        String email   = d.getString("email");
                        String label   = !TextUtils.isEmpty(display) ? display : email;
                        labels[i]      = label != null ? label : d.getId();
                        uids[i]        = d.getId();
                        checked[i]     = false;
                    }

                    android.view.View view = getLayoutInflater()
                            .inflate(R.layout.dialog_create_group, null, false);
                    final EditText etGroupName = view.findViewById(R.id.etGroupName);

                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Create group")
                            .setView(view)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Next: pick members", (dialog, which) -> {
                                String gName = etGroupName.getText() != null
                                        ? etGroupName.getText().toString().trim() : "";
                                if (TextUtils.isEmpty(gName)) {
                                    Toast.makeText(this, "Group name required", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                new androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("Select members for " + gName)
                                        .setMultiChoiceItems(labels, checked,
                                                (dlg, which2, isChecked2) -> checked[which2] = isChecked2)
                                        .setPositiveButton("OK", (dlg2, w2) -> {
                                            List<String> members = new ArrayList<>();
                                            for (int i = 0; i < uids.length; i++) {
                                                if (checked[i]) members.add(uids[i]);
                                            }
                                            if (members.isEmpty()) {
                                                Toast.makeText(this, "No members selected", Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                            AssignmentGroup g = new AssignmentGroup();
                                            g.groupId = "g" + (groupList.size() + 1);
                                            g.groupName = gName;
                                            g.memberUids.addAll(members);
                                            groupList.add(g);

                                            Toast.makeText(this,
                                                    "Group \"" + gName + "\" added (" + members.size() + " members)",
                                                    Toast.LENGTH_SHORT).show();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            })
                            .show();

                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Failed to load students: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setUiBusy(final boolean busy) {
        runOnUiThread(() -> {
            if (busy) {
                progress.show();
                btnCreate.setEnabled(false);
                btnCreate.setAlpha(0.6f);
                btnPickFile.setEnabled(false);
                btnPickFile.setAlpha(0.6f);
                if (btnPickStudents != null) { btnPickStudents.setEnabled(false); btnPickStudents.setAlpha(0.6f); }
                if (btnManageGroups != null) { btnManageGroups.setEnabled(false); btnManageGroups.setAlpha(0.6f); }
            } else {
                if (progress.isShowing()) progress.dismiss();
                btnCreate.setEnabled(true);
                btnCreate.setAlpha(1f);
                btnPickFile.setEnabled(true);
                btnPickFile.setAlpha(1f);
                if (btnPickStudents != null) { btnPickStudents.setEnabled(true); btnPickStudents.setAlpha(1f); }
                if (btnManageGroups != null) { btnManageGroups.setEnabled(true); btnManageGroups.setAlpha(1f); }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) executor).shutdownNow();
        }
    }
}
