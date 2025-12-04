package com.satyam.assignmenttracker.Activities;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.satyam.assignmenttracker.models.Base;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AssignmentDetailsActivity extends Base {

    private static final String TAG = "AssignmentDetailsAct";

    TextView tvTitle, tvDesc, tvDue;
    TextView tvTeacherName, tvCreatedInfo, tvFileName, tvFileMeta;
    TextView tvCompletionStatus, tvDaysLeft, tvLastSubmitted;

    Button btnDownload, btnSubmit, btnSaveForLater;
    Button btnViewSubmissions;
    Button btnDownloadRemote; // open online

    // NEW: teacher-only actions
    Button btnEditAssignment;
    Button btnDeleteAssignment;

    ImageButton btnBack;
    ImageView ivFileIcon, ivTeacherAvatar;
    CardView attachmentCard;

    // Group UI
    CardView groupCard;
    TextView tvGroupHint;
    Button btnShowGroup;

    // Student progress
    CheckBox cbStepRead, cbStepDraft, cbStepFinal;
    TextInputEditText etMyNotes;
    Button btnSaveProgress;
    Button btnViewProgressHistory;
    CardView progressCard;

    String assignmentId;

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // This will hold *this student's* group members
    List<String> groupMemberUids = new ArrayList<>();
    String currentGroupName = null;

    // Teacher notes (private field on assignment)
    private String teacherNotes = null;
    private boolean isTeacherOrCreator = false;

    // Attachment state
    private String attachmentUrl = null;
    private String attachmentName = null;

    // Current assignment fields (for editing)
    private String currentTitle = null;
    private String currentDesc = null;
    private String currentDueString = null;
    private Long currentDueMillis = null;
    private String currentCreatedBy = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assignment_details);

        // Bind views
        btnBack = findViewById(R.id.btnBack);

        tvTitle = findViewById(R.id.tvTitle);
        tvDesc = findViewById(R.id.tvDesc);
        tvDue = findViewById(R.id.tvDue);

        attachmentCard = findViewById(R.id.attachmentCard);
        ivFileIcon = findViewById(R.id.ivFileIcon);
        tvFileName = findViewById(R.id.tvFileName);
        tvFileMeta = findViewById(R.id.tvFileMeta);
        btnDownload = findViewById(R.id.btnDownload);
        btnDownloadRemote = findViewById(R.id.btnDownloadRemote); // from XML

        ivTeacherAvatar = findViewById(R.id.ivTeacherAvatar);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvCreatedInfo = findViewById(R.id.tvCreatedInfo);

        tvCompletionStatus = findViewById(R.id.tvCompletionStatus);
        tvDaysLeft = findViewById(R.id.tvDaysLeft);
        tvLastSubmitted = findViewById(R.id.tvLastSubmitted);

        btnSubmit = findViewById(R.id.btnSubmit);
        btnSaveForLater = findViewById(R.id.btnSaveForLater);
        btnViewSubmissions = findViewById(R.id.btnViewSubmissions);

        // NEW teacher-only buttons (must exist in layout)
        btnEditAssignment = findViewById(R.id.btnEditAssignment);
        btnDeleteAssignment = findViewById(R.id.btnDeleteAssignment);

        // Group views
        groupCard = findViewById(R.id.groupCard);
        tvGroupHint = findViewById(R.id.tvGroupHint);
        btnShowGroup = findViewById(R.id.btnShowGroup);

        // Progress summary
        cbStepRead = findViewById(R.id.cbStepRead);
        cbStepDraft = findViewById(R.id.cbStepDraft);
        cbStepFinal = findViewById(R.id.cbStepFinal);
        etMyNotes = findViewById(R.id.etMyNotes);
        btnSaveProgress = findViewById(R.id.btnSaveProgress);
        btnViewProgressHistory = findViewById(R.id.btnViewProgressHistory);
        progressCard = findViewById(R.id.progressCard);

        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        // assignmentId may come as "assignmentId" or "id"
        assignmentId = getIntent() != null
                ? (getIntent().getStringExtra("assignmentId") != null
                ? getIntent().getStringExtra("assignmentId")
                : getIntent().getStringExtra("id"))
                : null;

        if (TextUtils.isEmpty(assignmentId)) {
            Toast.makeText(this, "No assignment selected", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initial UI
        if (attachmentCard != null) attachmentCard.setVisibility(View.GONE);
        if (btnDownload != null) btnDownload.setVisibility(View.GONE);
        if (btnDownloadRemote != null) btnDownloadRemote.setVisibility(View.GONE);
        if (btnViewSubmissions != null) btnViewSubmissions.setVisibility(View.GONE);

        if (groupCard != null) groupCard.setVisibility(View.GONE);
        if (btnShowGroup != null) btnShowGroup.setVisibility(View.GONE);

        // Hide teacher buttons initially
        if (btnEditAssignment != null) btnEditAssignment.setVisibility(View.GONE);
        if (btnDeleteAssignment != null) btnDeleteAssignment.setVisibility(View.GONE);

        // Submit button → SubmissionActivity
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                Intent i = new Intent(this, SubmissionActivity.class);
                i.putExtra("assignmentId", assignmentId);
                startActivity(i);
            });
        }

        // Save to downloads → same downloader as btnDownload
        if (btnSaveForLater != null) {
            btnSaveForLater.setOnClickListener(v -> {
                if (TextUtils.isEmpty(attachmentUrl)) {
                    Toast.makeText(this, "No attachment to download", Toast.LENGTH_SHORT).show();
                    return;
                }
                String name = !TextUtils.isEmpty(attachmentName)
                        ? attachmentName
                        : ("attachment_" + System.currentTimeMillis());
                downloadRemoteFile(attachmentUrl, name);
            });
        }

        if (btnSaveProgress != null) {
            btnSaveProgress.setOnClickListener(v -> saveMyProgress());
        }

        if (btnViewProgressHistory != null) {
            btnViewProgressHistory.setOnClickListener(v -> openProgressTimeline());
        }

        if (btnShowGroup != null) {
            btnShowGroup.setOnClickListener(v -> showGroupMembersDialog());
        }

        // Teacher action buttons (click listeners; visibility controlled later)
        if (btnEditAssignment != null) {
            btnEditAssignment.setOnClickListener(v -> openEditAssignmentDialog());
        }
        if (btnDeleteAssignment != null) {
            btnDeleteAssignment.setOnClickListener(v -> confirmDeleteAssignment());
        }

        loadAssignment();
    }

    // ------------------- LOAD ASSIGNMENT -------------------

    private void loadAssignment() {
        db.collection("assignments").document(assignmentId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        Toast.makeText(this, "Assignment not found", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    String title = doc.getString("title");
                    String desc = doc.getString("description");
                    String dueStr = doc.getString("dueDate");
                    Long dueMillis = doc.getLong("dueDateMillis");
                    String fileUrl = doc.getString("fileUrl");
                    String fileName = doc.getString("fileName");
                    String fileSize = doc.getString("fileSize");
                    String createdBy = doc.getString("createdBy");

                    // store assignment info for editing
                    currentTitle = title;
                    currentDesc = desc;
                    currentDueString = dueStr;
                    currentDueMillis = dueMillis;
                    currentCreatedBy = createdBy;

                    // store attachment info for later clicks
                    attachmentUrl = fileUrl;
                    attachmentName = fileName;

                    // 🔹 Teacher notes (optional)
                    teacherNotes = doc.getString("teacherNotes");

                    if (tvTitle != null) tvTitle.setText(!TextUtils.isEmpty(title) ? title : "(no title)");
                    if (tvDesc != null) tvDesc.setText(!TextUtils.isEmpty(desc) ? desc : "");

                    if (tvDue != null) {
                        String dueDisplay = "--";
                        if (!TextUtils.isEmpty(dueStr)) {
                            dueDisplay = dueStr;
                        } else if (dueMillis != null) {
                            dueDisplay = DateFormat.format("dd MMM yyyy, hh:mm a", new Date(dueMillis)).toString();
                        }
                        tvDue.setText("Due: " + dueDisplay);
                    }

                    // 🔹 Attachment from Cloudinary
                    if (!TextUtils.isEmpty(fileUrl)) {
                        if (attachmentCard != null) attachmentCard.setVisibility(View.VISIBLE);
                        if (tvFileName != null)
                            tvFileName.setText(!TextUtils.isEmpty(fileName) ? fileName : "Attachment");
                        if (tvFileMeta != null) tvFileMeta.setText(!TextUtils.isEmpty(fileSize) ? fileSize : "");

                        // safe filename for download
                        final String safeName = !TextUtils.isEmpty(fileName)
                                ? fileName
                                : ("attachment_" + System.currentTimeMillis());

                        // Download to device
                        if (btnDownload != null) {
                            btnDownload.setVisibility(View.VISIBLE);
                            btnDownload.setText("Download");
                            btnDownload.setOnClickListener(v ->
                                    downloadRemoteFile(fileUrl, safeName)
                            );
                        }

                        // Open online (browser / viewer)
                        if (btnDownloadRemote != null) {
                            btnDownloadRemote.setVisibility(View.VISIBLE);
                            btnDownloadRemote.setText("Open online");
                            btnDownloadRemote.setOnClickListener(v -> openUrl(fileUrl));
                        }

                    } else {
                        if (attachmentCard != null) attachmentCard.setVisibility(View.GONE);
                        if (btnDownload != null) btnDownload.setVisibility(View.GONE);
                        if (btnDownloadRemote != null) btnDownloadRemote.setVisibility(View.GONE);
                    }

                    // 🔹 Figure out THIS student's group from assignment.groups[]
                    String currentUserUid = mAuth.getCurrentUser() != null
                            ? mAuth.getCurrentUser().getUid()
                            : null;

                    currentGroupName = null;
                    groupMemberUids.clear();

                    if (!TextUtils.isEmpty(currentUserUid)) {
                        Object groupsObj = doc.get("groups");
                        if (groupsObj instanceof List) {
                            List<?> rawGroups = (List<?>) groupsObj;
                            for (Object gObj : rawGroups) {
                                if (gObj instanceof Map) {
                                    Map<?, ?> gm = (Map<?, ?>) gObj;
                                    Object membersObj = gm.get("memberUids");
                                    if (membersObj instanceof List) {
                                        List<?> membersRaw = (List<?>) membersObj;
                                        boolean isInGroup = false;
                                        List<String> memberIds = new ArrayList<>();
                                        for (Object m : membersRaw) {
                                            if (m instanceof String) {
                                                String su = (String) m;
                                                memberIds.add(su);
                                                if (su.equals(currentUserUid)) {
                                                    isInGroup = true;
                                                }
                                            }
                                        }
                                        if (isInGroup) {
                                            Object nameObj = gm.get("groupName");
                                            currentGroupName = nameObj instanceof String ? (String) nameObj : null;
                                            groupMemberUids.addAll(memberIds);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 🔹 Update group card visibility & text for student
                    if (groupCard != null) {
                        if (!TextUtils.isEmpty(currentGroupName) && !groupMemberUids.isEmpty()) {
                            groupCard.setVisibility(View.VISIBLE);
                            if (tvGroupHint != null) {
                                tvGroupHint.setText("You are in group: " + currentGroupName +
                                        ". Tap below to see who is with you.");
                            }
                            if (btnShowGroup != null) btnShowGroup.setVisibility(View.VISIBLE);
                        } else {
                            groupCard.setVisibility(View.GONE);
                            if (btnShowGroup != null) btnShowGroup.setVisibility(View.GONE);
                        }
                    }

                    // 🔹 Teacher info
                    if (!TextUtils.isEmpty(createdBy)) {
                        db.collection("users").document(createdBy).get()
                                .addOnSuccessListener(userDoc -> {
                                    String display = null, email = null;
                                    if (userDoc != null && userDoc.exists()) {
                                        display = userDoc.getString("displayName");
                                        email = userDoc.getString("email");
                                    }
                                    final String label = !TextUtils.isEmpty(display)
                                            ? display
                                            : (!TextUtils.isEmpty(email) ? email : createdBy);
                                    if (tvTeacherName != null) tvTeacherName.setText(label);

                                    if (tvCreatedInfo != null) {
                                        Long ts = doc.getLong("timestamp");
                                        String createdAt = ts != null
                                                ? DateFormat.format("dd MMM yyyy", new Date(ts)).toString()
                                                : "—";
                                        tvCreatedInfo.setText("Assigned on " + createdAt);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (tvTeacherName != null) tvTeacherName.setText(createdBy);
                                    if (tvCreatedInfo != null)
                                        tvCreatedInfo.setText("Assigned by " + createdBy);
                                });
                    }

                    // 🔹 Status & days/time left
                    computeStatusAndDaysLeft(dueMillis);

                    // 🔹 Last submission info
                    loadLastSubmissionForCurrentUser();

                    // 🔹 Progress summary load
                    loadMyProgress();

                    // 🔹 Role-based UI (teacher vs student)
                    String uid = mAuth.getCurrentUser() != null
                            ? mAuth.getCurrentUser().getUid()
                            : null;
                    if (uid != null && uid.equals(createdBy)) {
                        // creator treated as teacher
                        showSubmissionsButton(true);
                        applyRoleVisibility(true);
                    } else if (uid != null) {
                        db.collection("users").document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc != null && userDoc.exists()) {
                                        String role = userDoc.getString("role");
                                        boolean isTeacher = "teacher".equalsIgnoreCase(role);
                                        showSubmissionsButton(isTeacher);
                                        applyRoleVisibility(isTeacher);
                                    } else {
                                        showSubmissionsButton(false);
                                        applyRoleVisibility(false);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    showSubmissionsButton(false);
                                    applyRoleVisibility(false);
                                });
                    } else {
                        showSubmissionsButton(false);
                        applyRoleVisibility(false);
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadAssignment failed", e);
                    Toast.makeText(this, "Error loading assignment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ------------------- ROLE VISIBILITY -------------------

    private void applyRoleVisibility(boolean isTeacherOrCreatorFlag) {
        this.isTeacherOrCreator = isTeacherOrCreatorFlag;

        if (isTeacherOrCreatorFlag) {
            // Teacher view: hide student submit + progress + group
            if (btnSubmit != null) btnSubmit.setVisibility(View.GONE);
            if (btnSaveForLater != null) btnSaveForLater.setVisibility(View.GONE);

            if (progressCard != null) progressCard.setVisibility(View.GONE);
            if (btnSaveProgress != null) btnSaveProgress.setVisibility(View.GONE);
            if (btnViewProgressHistory != null) btnViewProgressHistory.setVisibility(View.GONE);

            if (groupCard != null) groupCard.setVisibility(View.GONE);
            if (btnShowGroup != null) btnShowGroup.setVisibility(View.GONE);

            // Show teacher controls
            if (btnEditAssignment != null) btnEditAssignment.setVisibility(View.VISIBLE);
            if (btnDeleteAssignment != null) btnDeleteAssignment.setVisibility(View.VISIBLE);

            // 🔹 Teacher notes: long-press on teacher name to edit private notes
            if (tvTeacherName != null) {
                tvTeacherName.setOnLongClickListener(v -> {
                    showTeacherNotesDialog();
                    return true;
                });
                Toast.makeText(this,
                        "Hint: Long-press on your name to add private notes.",
                        Toast.LENGTH_LONG).show();
            }

        } else {
            // Student view: show submit & progress; group visibility was already decided in loadAssignment()
            if (btnSubmit != null) btnSubmit.setVisibility(View.VISIBLE);
            if (btnSaveForLater != null) btnSaveForLater.setVisibility(View.VISIBLE);

            if (progressCard != null) progressCard.setVisibility(View.VISIBLE);
            if (btnSaveProgress != null) btnSaveProgress.setVisibility(View.VISIBLE);
            if (btnViewProgressHistory != null) btnViewProgressHistory.setVisibility(View.VISIBLE);

            // Hide teacher-only buttons
            if (btnEditAssignment != null) btnEditAssignment.setVisibility(View.GONE);
            if (btnDeleteAssignment != null) btnDeleteAssignment.setVisibility(View.GONE);

            // No notes UI for students
            if (tvTeacherName != null) {
                tvTeacherName.setOnLongClickListener(null);
            }
        }
    }

    // ------------------- STATUS / DAYS + TIME LEFT -------------------

    private void computeStatusAndDaysLeft(Long dueMillis) {
        if (tvCompletionStatus != null) tvCompletionStatus.setText("Not submitted");
        if (tvDaysLeft != null) tvDaysLeft.setText("Due in: --");

        long now = System.currentTimeMillis();

        if (dueMillis != null) {
            long diff = dueMillis - now;

            if (diff <= 0) {
                // Past due (time-aware)
                if (tvDaysLeft != null) tvDaysLeft.setText("Due: EXPIRED");
            } else {
                long days = diff / (1000L * 60 * 60 * 24);
                long hours = (diff / (1000L * 60 * 60)) % 24;
                long minutes = (diff / (1000L * 60)) % 60;

                String timeLeft;
                if (days > 0) {
                    timeLeft = days + " day" + (days > 1 ? "s" : "");
                    if (hours > 0) {
                        timeLeft += ", " + hours + "h";
                    }
                } else if (hours > 0) {
                    timeLeft = hours + "h " + minutes + "m";
                } else {
                    timeLeft = minutes + "m";
                }

                if (tvDaysLeft != null) {
                    tvDaysLeft.setText("Due in: " + timeLeft);
                }
            }
        }

        // Submission-based status
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        db.collection("assignments").document(assignmentId)
                .collection("submissions")
                .document(uid)
                .get()
                .addOnSuccessListener(sub -> {
                    if (sub != null && sub.exists()) {
                        String status = sub.getString("status");
                        Boolean uploading = sub.getBoolean("uploading");

                        String displayStatus;
                        if (uploading != null && uploading) {
                            displayStatus = "Uploading...";
                        } else if (!TextUtils.isEmpty(status)) {
                            if ("graded".equalsIgnoreCase(status)) {
                                displayStatus = "Graded";
                            } else if ("submitted".equalsIgnoreCase(status)) {
                                displayStatus = "Submitted";
                            } else {
                                displayStatus = status;
                            }
                        } else {
                            displayStatus = "Submitted";
                        }

                        if (tvCompletionStatus != null) tvCompletionStatus.setText(displayStatus);
                    } else {
                        if (currentDueMillis != null && currentDueMillis < System.currentTimeMillis()) {
                            if (tvCompletionStatus != null) tvCompletionStatus.setText("Overdue");
                        } else {
                            if (tvCompletionStatus != null) tvCompletionStatus.setText("Not submitted");
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Log.w(TAG, "computeStatusAndDaysLeft: submission check failed", e)
                );
    }

    // ------------------- LAST SUBMISSION -------------------

    private void loadLastSubmissionForCurrentUser() {
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null || tvLastSubmitted == null) {
            if (tvLastSubmitted != null) tvLastSubmitted.setText("Last submitted: --");
            return;
        }

        db.collection("assignments").document(assignmentId)
                .collection("submissions")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        Long ts = null;
                        Object tsObj = doc.get("submittedAt");
                        if (tsObj instanceof Number) ts = ((Number) tsObj).longValue();

                        String when = ts != null
                                ? DateFormat.format("dd MMM yyyy, hh:mm a", new Date(ts)).toString()
                                : "—";

                        String status = doc.getString("status");
                        if (status == null) status = "submitted";

                        tvLastSubmitted.setText("Last submitted: " + when + " (" + status + ")");

                        String grade = doc.getString("grade");
                        Object marksObj = doc.get("marks");
                        Double marks = marksObj instanceof Number ? ((Number) marksObj).doubleValue() : null;

                        if ("graded".equalsIgnoreCase(status) && (grade != null || marks != null)) {
                            StringBuilder sb = new StringBuilder(tvLastSubmitted.getText());
                            sb.append("\n");
                            if (grade != null) sb.append("Grade: ").append(grade).append("  ");
                            if (marks != null) sb.append("Marks: ").append(marks);
                            tvLastSubmitted.setText(sb.toString());
                        }

                    } else {
                        tvLastSubmitted.setText("Last submitted: none");
                    }
                })
                .addOnFailureListener(e -> tvLastSubmitted.setText("Last submitted: --"));
    }

    // ------------------- TEACHER SUBMISSIONS BUTTON -------------------

    private void showSubmissionsButton(boolean show) {
        if (btnViewSubmissions == null) return;
        btnViewSubmissions.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            btnViewSubmissions.setOnClickListener(v -> {
                Intent i = new Intent(AssignmentDetailsActivity.this, TeacherSubmissionsActivity.class);
                i.putExtra("assignmentId", assignmentId);
                startActivity(i);
            });
        } else {
            btnViewSubmissions.setOnClickListener(null);
        }
    }

    // ------------------- STUDENT PROGRESS SUMMARY -------------------

    private void loadMyProgress() {
        if (mAuth == null) return;
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null || TextUtils.isEmpty(assignmentId)) return;

        if (cbStepRead == null && cbStepDraft == null && cbStepFinal == null && etMyNotes == null) {
            return;
        }

        db.collection("assignments")
                .document(assignmentId)
                .collection("submissions")
                .document(uid)
                .collection("progress")
                .document("summary")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    Boolean stepRead = doc.getBoolean("stepRead");
                    Boolean stepDraft = doc.getBoolean("stepDraft");
                    Boolean stepFinal = doc.getBoolean("stepFinal");
                    String notes = doc.getString("notes");

                    if (cbStepRead != null && stepRead != null) cbStepRead.setChecked(stepRead);
                    if (cbStepDraft != null && stepDraft != null) cbStepDraft.setChecked(stepDraft);
                    if (cbStepFinal != null && stepFinal != null) cbStepFinal.setChecked(stepFinal);
                    if (etMyNotes != null && notes != null) etMyNotes.setText(notes);
                })
                .addOnFailureListener(e ->
                        Log.w(TAG, "loadMyProgress failed: " + e.getMessage())
                );
    }

    private void saveMyProgress() {
        if (mAuth == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(assignmentId)) {
            Toast.makeText(this, "No assignment", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean stepRead = cbStepRead != null && cbStepRead.isChecked();
        boolean stepDraft = cbStepDraft != null && cbStepDraft.isChecked();
        boolean stepFinal = cbStepFinal != null && cbStepFinal.isChecked();

        String notes = "";
        if (etMyNotes != null && etMyNotes.getText() != null) {
            notes = etMyNotes.getText().toString().trim();
        }

        long now = System.currentTimeMillis();

        // Summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("stepRead", stepRead);
        summary.put("stepDraft", stepDraft);
        summary.put("stepFinal", stepFinal);
        summary.put("notes", notes);
        summary.put("updatedAt", now);
        summary.put("updatedBy", uid);

        db.collection("assignments")
                .document(assignmentId)
                .collection("submissions")
                .document(uid)
                .collection("progress")
                .document("summary")
                .set(summary, SetOptions.merge())
                .addOnSuccessListener(a ->
                        Toast.makeText(this, "Progress saved", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Could not save progress: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );

        // Timeline log entry
        if (!TextUtils.isEmpty(notes) || stepRead || stepDraft || stepFinal) {
            Map<String, Object> log = new HashMap<>();
            log.put("text", notes);
            log.put("createdAt", now);
            log.put("createdBy", uid);
            log.put("stepRead", stepRead);
            log.put("stepDraft", stepDraft);
            log.put("stepFinal", stepFinal);

            db.collection("assignments")
                    .document(assignmentId)
                    .collection("submissions")
                    .document(uid)
                    .collection("progressLogs")
                    .add(log)
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Failed to add progress log: " + e.getMessage())
                    );
        }
    }

    private void openProgressTimeline() {
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, ProgressTimelineActivity.class);
        i.putExtra("assignmentId", assignmentId);
        i.putExtra("studentUid", uid);
        startActivity(i);
    }

    // ------------------- GROUP MEMBERS DIALOG -------------------

    private void showGroupMembersDialog() {
        if (groupMemberUids == null || groupMemberUids.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Group members")
                    .setMessage("No group members found for you on this assignment.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            final StringBuilder sb = new StringBuilder();
            final AtomicInteger done = new AtomicInteger(0);
            for (String uid : groupMemberUids) {
                if (TextUtils.isEmpty(uid)) {
                    done.incrementAndGet();
                    if (done.get() == groupMemberUids.size()) showGroupDialogFromBuilder(sb);
                    continue;
                }
                db.collection("users").document(uid).get()
                        .addOnSuccessListener(userDoc -> {
                            String name = null, email = null;
                            if (userDoc != null && userDoc.exists()) {
                                name = userDoc.getString("displayName");
                                email = userDoc.getString("email");
                            }
                            String line = !TextUtils.isEmpty(name)
                                    ? name
                                    : (!TextUtils.isEmpty(email) ? email : uid);
                            sb.append(line).append("\n");
                            if (done.incrementAndGet() == groupMemberUids.size())
                                showGroupDialogFromBuilder(sb);
                        })
                        .addOnFailureListener(e -> {
                            sb.append(uid).append("\n");
                            if (done.incrementAndGet() == groupMemberUids.size())
                                showGroupDialogFromBuilder(sb);
                        });
            }
        }
    }

    private void showGroupDialogFromBuilder(StringBuilder sb) {
        String groupTitle = !TextUtils.isEmpty(currentGroupName)
                ? "Group: " + currentGroupName
                : "Group members";

        String content = sb.length() == 0 ? "No members found" : sb.toString().trim();
        new android.app.AlertDialog.Builder(this)
                .setTitle(groupTitle)
                .setMessage(content)
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------- TEACHER NOTES (PRIVATE) -------------------

    private void showTeacherNotesDialog() {
        if (!isTeacherOrCreator) {
            return; // safety
        }

        EditText editText = new EditText(this);
        editText.setHint("Your private notes for this assignment");
        editText.setMinLines(3);
        editText.setMaxLines(6);
        editText.setText(teacherNotes != null ? teacherNotes : "");

        new android.app.AlertDialog.Builder(this)
                .setTitle("Teacher Notes")
                .setView(editText)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newNotes = editText.getText() != null
                            ? editText.getText().toString().trim()
                            : "";
                    saveTeacherNotes(newNotes);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveTeacherNotes(String notes) {
        if (TextUtils.isEmpty(assignmentId)) {
            Toast.makeText(this, "No assignment", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("teacherNotes", notes);
        updates.put("teacherNotesUpdatedAt", FieldValue.serverTimestamp());
        updates.put("teacherNotesUpdatedBy", uid);

        db.collection("assignments")
                .document(assignmentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    teacherNotes = notes;
                    Toast.makeText(this, "Notes saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save notes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ------------------- EDIT ASSIGNMENT (TEACHER) -------------------

    private void openEditAssignmentDialog() {
        if (!isTeacherOrCreator) {
            Toast.makeText(this, "Only teachers can edit this assignment", Toast.LENGTH_SHORT).show();
            return;
        }

        // simple vertical layout with 2 EditTexts (title + description)
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        final EditText etTitle = new EditText(this);
        etTitle.setHint("Title");
        etTitle.setSingleLine(true);
        etTitle.setText(currentTitle != null ? currentTitle : "");
        container.addView(etTitle);

        final EditText etDesc = new EditText(this);
        etDesc.setHint("Description");
        etDesc.setMinLines(3);
        etDesc.setMaxLines(6);
        etDesc.setText(currentDesc != null ? currentDesc : "");
        container.addView(etDesc);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Edit Assignment")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = etTitle.getText() != null
                            ? etTitle.getText().toString().trim()
                            : "";
                    String newDesc = etDesc.getText() != null
                            ? etDesc.getText().toString().trim()
                            : "";

                    if (TextUtils.isEmpty(newTitle)) {
                        Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("title", newTitle);
                    updates.put("description", newDesc);

                    db.collection("assignments").document(assignmentId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                currentTitle = newTitle;
                                currentDesc = newDesc;
                                if (tvTitle != null) tvTitle.setText(newTitle);
                                if (tvDesc != null) tvDesc.setText(newDesc);
                                Toast.makeText(this, "Assignment updated", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Failed to update: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show()
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteAssignment() {
        if (!isTeacherOrCreator) {
            Toast.makeText(this, "Only teachers can delete this assignment", Toast.LENGTH_SHORT).show();
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete assignment?")
                .setMessage("This will remove the assignment for all students. You cannot undo this action.")
                .setPositiveButton("Delete", (dialog, which) -> deleteAssignment())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAssignment() {
        if (TextUtils.isEmpty(assignmentId)) {
            Toast.makeText(this, "No assignment", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("assignments").document(assignmentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Assignment deleted", Toast.LENGTH_SHORT).show();
                    finish(); // close details screen
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to delete: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    // ------------------- ATTACHMENT OPEN / DOWNLOAD -------------------

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadRemoteFile(String url, String fileName) {
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "No URL to download", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                Toast.makeText(this, "Download manager unavailable", Toast.LENGTH_LONG).show();
                return;
            }

            Uri uri = Uri.parse(url);

            // Clean filename (avoid weird chars)
            String cleanName = fileName;
            if (TextUtils.isEmpty(cleanName)) {
                cleanName = "attachment_" + System.currentTimeMillis();
            }
            cleanName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_");

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(cleanName);
            request.setDescription("Downloading assignment attachment");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            // Allow Wi-Fi + mobile, roaming ok
            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE
            );
            request.setAllowedOverRoaming(true);

            // App-specific downloads dir – no special permission needed
            request.setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    cleanName
            );

            // Optional mime based on extension
            String mime = getMimeTypeFromName(cleanName);
            if (mime != null) {
                request.setMimeType(mime);
            }

            dm.enqueue(request);
            Toast.makeText(this, "Download started – check notifications", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "downloadRemoteFile failed", e);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getMimeTypeFromName(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "application/msword";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "application/vnd.ms-excel";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".txt")) return "text/plain";
        return null;
    }
}
