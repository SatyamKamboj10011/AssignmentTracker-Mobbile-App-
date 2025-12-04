package com.satyam.assignmenttracker.Activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

// ROOM: local draft imports
import com.satyam.assignmenttracker.Adapters.CloudinaryUploader;
import com.satyam.assignmenttracker.R;
import com.satyam.assignmenttracker.local.AppDatabase;
import com.satyam.assignmenttracker.local.DraftSubmissionDao;
import com.satyam.assignmenttracker.local.DraftSubmissionEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubmissionActivity extends AppCompatActivity {
    private static final String TAG = "SubmissionAct";
    private static final int PICK_FILE_REQ = 3001;

    TextView tvAssignmentTitle, tvAssignmentDue;
    TextView tvPickedName, tvExistingFile, tvUploadMsg;
    Button btnPick, btnUpload, btnClose, btnViewExisting, btnReplace;
    View existingLayout;

    // NEW: view grade button
    Button btnViewGrade;

    String assignmentId;
    Uri pickedUri;
    String pickedName;
    long pickedSize = -1;
    String pickedMime;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submission);

        tvAssignmentTitle = findViewById(R.id.tvAssignmentTitle);
        tvAssignmentDue = findViewById(R.id.tvAssignmentDue);
        tvPickedName = findViewById(R.id.tvPickedName);
        tvExistingFile = findViewById(R.id.tvExistingFile);
        tvUploadMsg = findViewById(R.id.tvUploadMsg);

        btnPick = findViewById(R.id.btnPick);
        btnUpload = findViewById(R.id.btnUpload);
        btnClose = findViewById(R.id.btnClose);
        btnViewExisting = findViewById(R.id.btnViewExisting);
        btnReplace = findViewById(R.id.btnReplace);
        existingLayout = findViewById(R.id.existingSubmissionLayout);

        // bind new button (make sure it exists in XML)
        btnViewGrade = findViewById(R.id.btnViewGrade);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Please wait...");

        assignmentId = getIntent().getStringExtra("assignmentId");
        if (TextUtils.isEmpty(assignmentId)) {
            Toast.makeText(this, "No assignment specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ROOM: try to restore any local draft before doing network
        restoreDraftAsync();

        // optional load title/due
        db.collection("assignments").document(assignmentId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String t = doc.getString("title");
                        String due = doc.getString("dueDate");
                        tvAssignmentTitle.setText(t != null ? t : "(no title)");
                        tvAssignmentDue.setText(due != null ? "Due: " + due : "");
                    }
                });

        // 🔁 PICK FILE (use ACTION_OPEN_DOCUMENT so we can get SAF permissions)
        btnPick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(Intent.createChooser(i, "Choose file"), PICK_FILE_REQ);
        });

        btnUpload.setOnClickListener(v -> {
            if (pickedUri == null) {
                Toast.makeText(this, "Pick a file first", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadPickedFileToCloudinary();
        });

        btnClose.setOnClickListener(v -> finish());

        btnViewExisting.setOnClickListener(v -> {
            String url = (String) tvExistingFile.getTag();
            if (!TextUtils.isEmpty(url)) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } else {
                Toast.makeText(this, "No existing file", Toast.LENGTH_SHORT).show();
            }
        });

        // 🔁 REPLACE FILE (also ACTION_OPEN_DOCUMENT)
        btnReplace.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(Intent.createChooser(i, "Choose file to replace"), PICK_FILE_REQ);
        });

        // NEW: set up view grade button
        if (btnViewGrade != null) {
            btnViewGrade.setOnClickListener(v -> showGradeDialog());
            btnViewGrade.setVisibility(View.GONE); // hidden until we know a submission exists
        }

        btnUpload.setEnabled(false);
        checkExistingSubmission();
    }

    private void checkExistingSubmission() {
        String uid = getCurrentUid();
        if (uid == null) return;

        db.collection("assignments")
                .document(assignmentId)
                .collection("submissions")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String fileName = doc.getString("fileName");
                        String fileUrl = doc.getString("fileUrl");
                        tvExistingFile.setText(fileName != null ? fileName : "(file)");
                        tvExistingFile.setTag(fileUrl != null ? fileUrl : "");
                        existingLayout.setVisibility(View.VISIBLE);

                        // if there is a submission -> allow viewing grade
                        if (btnViewGrade != null) btnViewGrade.setVisibility(View.VISIBLE);
                    } else {
                        existingLayout.setVisibility(View.GONE);
                        if (btnViewGrade != null) btnViewGrade.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "checkExistingSubmission failed", e);
                    existingLayout.setVisibility(View.GONE);
                    if (btnViewGrade != null) btnViewGrade.setVisibility(View.GONE);
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQ && resultCode == RESULT_OK && data != null && data.getData() != null) {
            pickedUri = data.getData();

            // 🔑 VERY IMPORTANT: take persistable read permission for this URI (SAF / Downloads)
            final int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(pickedUri, takeFlags);
            } catch (SecurityException e) {
                // Not all providers support persistable permissions; safe to ignore in that case.
                Log.w(TAG, "takePersistableUriPermission failed: " + e.getMessage());
            }

            resolvePickedInfo(pickedUri);
        }
    }

    private void resolvePickedInfo(Uri uri) {
        String name = null;
        long size = -1;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nIdx >= 0) name = cursor.getString(nIdx);
                if (sIdx >= 0) size = cursor.getLong(sIdx);
            }
        } catch (Exception e) {
            Log.w(TAG, "resolvePickedInfo", e);
        }
        if (name == null) name = uri.getLastPathSegment();
        if (name == null) name = "submission";

        pickedName = sanitizeFileName(name);
        pickedSize = size;
        pickedMime = getContentResolver().getType(uri);

        tvPickedName.setText(pickedName + (pickedSize > 0 ? " • " + (pickedSize / 1024) + " KB" : ""));
        btnUpload.setEnabled(true);

        // ROOM: save draft whenever user picks/changes file
        saveDraftAsync();
    }

    /**
     * Uploads the picked file to Cloudinary and tags the submission with groupId/groupName,
     * based on the assignment's "groups" array.
     */
    private void uploadPickedFileToCloudinary() {
        final String uid = getCurrentUid();
        if (uid == null) {
            Toast.makeText(this, "Sign in to submit", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pickedUri == null) {
            Toast.makeText(this, "Choose a file first", Toast.LENGTH_SHORT).show();
            return;
        }

        progress.setMessage("Uploading submission...");
        progress.show();
        btnUpload.setEnabled(false);

        final long ts = System.currentTimeMillis();
        final String safeName = pickedName != null ? pickedName : ("submission_" + ts);

        // Reference to student's submission doc
        final com.google.firebase.firestore.DocumentReference subRef = db.collection("assignments")
                .document(assignmentId)
                .collection("submissions")
                .document(uid);

        // STEP 1: read assignment to detect group membership
        db.collection("assignments")
                .document(assignmentId)
                .get()
                .addOnSuccessListener(assignmentDoc -> {
                    // Build initial map
                    final Map<String, Object> initial = new HashMap<>();
                    initial.put("studentUid", uid);
                    initial.put("submittedAt", ts);
                    initial.put("fileName", safeName);
                    initial.put("fileUrl", null);
                    initial.put("uploading", true);
                    initial.put("status", "uploading");

                    // Try attach group info if groups array exists
                    attachGroupInfoFromAssignment(assignmentDoc, uid, initial);

                    // STEP 2: create/overwrite submission doc with uploading=true + group info
                    subRef.set(initial)
                            .addOnSuccessListener(aVoid -> {
                                // STEP 3: upload in background as before
                                startCloudinaryUploadThread(subRef, safeName, ts);
                            })
                            .addOnFailureListener(e -> {
                                progress.dismiss();
                                btnUpload.setEnabled(true);
                                Toast.makeText(this, "Could not create submission doc: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to read assignment for group info: " + e.getMessage());

                    // Fallback: continue without group info
                    final Map<String, Object> initial = new HashMap<>();
                    initial.put("studentUid", uid);
                    initial.put("submittedAt", ts);
                    initial.put("fileName", safeName);
                    initial.put("fileUrl", null);
                    initial.put("uploading", true);
                    initial.put("status", "uploading");

                    subRef.set(initial)
                            .addOnSuccessListener(aVoid -> {
                                startCloudinaryUploadThread(subRef, safeName, ts);
                            })
                            .addOnFailureListener(ex -> {
                                progress.dismiss();
                                btnUpload.setEnabled(true);
                                Toast.makeText(this, "Could not create submission doc: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });
    }

    /**
     * Reads "groups" array from assignment doc and, if this student belongs to a group,
     * adds "groupId" and "groupName" into the map.
     */
    @SuppressWarnings("unchecked")
    private void attachGroupInfoFromAssignment(DocumentSnapshot assignmentDoc,
                                               String uid,
                                               Map<String, Object> target) {
        if (assignmentDoc == null || !assignmentDoc.exists()) return;

        Object groupsObj = assignmentDoc.get("groups");
        if (!(groupsObj instanceof List)) return;

        List<Object> groups = (List<Object>) groupsObj;
        for (Object gObj : groups) {
            if (!(gObj instanceof Map)) continue;
            Map<String, Object> g = (Map<String, Object>) gObj;

            Object membersObj = g.get("memberUids");
            if (!(membersObj instanceof List)) continue;
            List<?> members = (List<?>) membersObj;

            if (members.contains(uid)) {
                Object gid = g.get("groupId");
                Object gname = g.get("groupName");
                if (gid instanceof String) {
                    target.put("groupId", (String) gid);
                }
                if (gname instanceof String) {
                    target.put("groupName", (String) gname);
                }
                break; // one group is enough
            }
        }
    }

    /**
     * Starts the background thread that uploads to Cloudinary
     * and updates the submission doc with fileUrl + status.
     */
    private void startCloudinaryUploadThread(final com.google.firebase.firestore.DocumentReference subRef,
                                             final String safeName,
                                             final long ts) {
        new Thread(() -> {
            try {
                String secureUrl = CloudinaryUploader.uploadUriStreaming(
                        getContentResolver(), pickedUri, safeName, pickedMime);
                Map<String, Object> upd = new HashMap<>();
                upd.put("fileUrl", secureUrl);
                upd.put("fileName", safeName);
                upd.put("submittedAt", ts);
                upd.put("uploading", false);
                upd.put("status", "submitted"); // student submitted, teacher will mark graded later

                subRef.update(upd)
                        .addOnSuccessListener(a -> {
                            // ROOM: remove local draft after successful upload
                            deleteDraftAsync();

                            runOnUiThread(() -> {
                                progress.dismiss();
                                btnUpload.setEnabled(true);
                                tvUploadMsg.setText("Uploaded successfully");
                                Toast.makeText(SubmissionActivity.this, "Submission uploaded", Toast.LENGTH_SHORT).show();
                                checkExistingSubmission();
                            });
                        })
                        .addOnFailureListener(e -> runOnUiThread(() -> {
                            progress.dismiss();
                            btnUpload.setEnabled(true);
                            tvUploadMsg.setText("");
                            Toast.makeText(SubmissionActivity.this, "Save URL failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }));
            } catch (Exception e) {
                Log.e(TAG, "upload to cloudinary failed", e);
                // mark uploading false and store error
                subRef.update("uploading", false, "uploadError", e.getMessage(), "status", "upload_failed")
                        .addOnCompleteListener(ignore -> runOnUiThread(() -> {
                            progress.dismiss();
                            btnUpload.setEnabled(true);
                            Toast.makeText(SubmissionActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }));
            }
        }).start();
    }

    // NEW: grade / feedback dialog for student
    private void showGradeDialog() {
        String uid = getCurrentUid();
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("assignments")
                .document(assignmentId)
                .collection("submissions")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        Toast.makeText(this, "No submission yet", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Inflate dialog layout
                    View view = getLayoutInflater().inflate(R.layout.dialog_student_grade, null, false);

                    TextView tvStatus   = view.findViewById(R.id.tvGradeStatus);
                    TextView tvGrade    = view.findViewById(R.id.tvGradeGrade);
                    TextView tvMarks    = view.findViewById(R.id.tvGradeMarks);
                    TextView tvStars    = view.findViewById(R.id.tvGradeStars);
                    TextView tvFeedback = view.findViewById(R.id.tvGradeFeedback);

                    String status   = doc.getString("status");
                    String grade    = doc.getString("grade");
                    String feedback = doc.getString("feedback");

                    Double marks = null;
                    Object marksObj = doc.get("marks");
                    if (marksObj instanceof Number) {
                        marks = ((Number) marksObj).doubleValue();
                    }

                    // Fill values (with fallbacks)
                    tvStatus.setText("Status: " + (TextUtils.isEmpty(status) ? "submitted" : status));
                    tvGrade.setText(!TextUtils.isEmpty(grade) ? grade : "--");

                    if (marks != null) {
                        tvMarks.setText(marks + " / 100");
                    } else {
                        tvMarks.setText("--");
                    }

                    // Simple stars from marks (0–100 -> 0–5)
                    if (marks != null) {
                        double val = marks;
                        if (val < 0) val = 0;
                        if (val > 100) val = 100;
                        int stars = (int) Math.round(val / 20.0); // 0..5
                        if (stars < 0) stars = 0;
                        if (stars > 5) stars = 5;

                        StringBuilder starStr = new StringBuilder();
                        for (int i = 0; i < stars; i++) starStr.append("★");
                        for (int i = stars; i < 5; i++) starStr.append("☆");
                        tvStars.setText(starStr.toString());
                    } else {
                        tvStars.setText("☆☆☆☆☆");
                    }

                    if (!TextUtils.isEmpty(feedback)) {
                        tvFeedback.setText(feedback);
                    } else {
                        tvFeedback.setText("No feedback yet. Your submission has been saved.");
                    }

                    new AlertDialog.Builder(this)
                            .setView(view)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not load grade: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    // ===== ROOM HELPERS =====

    private String getDraftId() {
        String uid = getCurrentUid();
        if (uid == null || TextUtils.isEmpty(assignmentId)) return null;
        return assignmentId + "_" + uid;
    }

    private void saveDraftAsync() {
        String uid = getCurrentUid();
        if (uid == null || pickedUri == null) return;

        String draftId = getDraftId();
        if (draftId == null) return;

        new Thread(() -> {
            try {
                AppDatabase dbLocal = AppDatabase.getInstance(getApplicationContext());
                DraftSubmissionDao dao = dbLocal.draftSubmissionDao();

                DraftSubmissionEntity e = new DraftSubmissionEntity();
                e.id = draftId;
                e.assignmentId = assignmentId;
                e.userId = uid;
                e.fileUri = pickedUri.toString();
                e.fileName = pickedName;
                e.fileSize = pickedSize;
                e.mimeType = pickedMime;
                e.lastEditedAt = System.currentTimeMillis();

                dao.upsert(e);
            } catch (Exception ex) {
                Log.w(TAG, "saveDraftAsync failed", ex);
            }
        }).start();
    }

    private void restoreDraftAsync() {
        new Thread(() -> {
            try {
                String draftId = getDraftId();
                if (draftId == null) return;

                AppDatabase dbLocal = AppDatabase.getInstance(getApplicationContext());
                DraftSubmissionDao dao = dbLocal.draftSubmissionDao();
                DraftSubmissionEntity draft = dao.getById(draftId);

                if (draft != null && draft.fileUri != null) {
                    pickedUri = Uri.parse(draft.fileUri);
                    pickedName = draft.fileName;
                    pickedSize = draft.fileSize;
                    pickedMime = draft.mimeType;

                    runOnUiThread(() -> {
                        tvPickedName.setText(
                                pickedName + (pickedSize > 0 ? " • " + (pickedSize / 1024) + " KB" : "")
                        );
                        btnUpload.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "restoreDraftAsync failed", e);
            }
        }).start();
    }

    private void deleteDraftAsync() {
        new Thread(() -> {
            try {
                String draftId = getDraftId();
                if (draftId == null) return;

                AppDatabase dbLocal = AppDatabase.getInstance(getApplicationContext());
                dbLocal.draftSubmissionDao().deleteById(draftId);
            } catch (Exception e) {
                Log.w(TAG, "deleteDraftAsync failed", e);
            }
        }).start();
    }

    // ===== UTILS =====

    private String getCurrentUid() {
        try {
            if (mAuth.getCurrentUser() != null) return mAuth.getCurrentUser().getUid();
        } catch (Exception ignored) {}
        return null;
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "submission";
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]+", "_");
        safe = safe.replaceAll("\\.+", ".");
        if (safe.length() > 120) safe = safe.substring(0, 120);
        if (!safe.contains(".")) safe = safe + ".bin";
        return safe;
    }
}
