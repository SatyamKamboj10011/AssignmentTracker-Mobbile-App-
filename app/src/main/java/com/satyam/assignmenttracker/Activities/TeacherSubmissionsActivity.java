package com.satyam.assignmenttracker.Activities;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows list of student submissions for an assignment.
 * - Teacher can: open / download / grade
 * - Row shows: student name + group name + member count
 * - Long-press row: shows all group members' names
 * - Grade dialog: option to apply grade to this student OR to whole group
 * - Sorting: ungraded submissions first, then graded, by submittedAt
 */
public class TeacherSubmissionsActivity extends AppCompatActivity {

    private static final String TAG = "TeacherSubmissionsAct";

    RecyclerView rv;
    SubAdapter adapter;
    List<SubmissionItem> list = new ArrayList<>();
    String assignmentId;

    FirebaseFirestore db;
    FirebaseAuth mAuth;
    ProgressDialog progress;

    // student UID -> group name
    private final Map<String, String> studentGroupNameMap = new HashMap<>();
    // group name -> list of member UIDs
    private final Map<String, List<String>> groupNameToMembersMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_submissions);

        rv = findViewById(R.id.rvSubmissions);
        assignmentId = getIntent().getStringExtra("assignmentId");
        if (TextUtils.isEmpty(assignmentId)) {
            Toast.makeText(this, "No assignment specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Loading submissions...");

        adapter = new SubAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        loadSubmissions();
    }

    // ------------------------ LOAD SUBMISSIONS + GROUPS ------------------------

    private void loadSubmissions() {
        progress.show();

        // 1) Load assignment to read groups[]
        db.collection("assignments").document(assignmentId).get()
                .addOnSuccessListener(assignDoc -> {
                    studentGroupNameMap.clear();
                    groupNameToMembersMap.clear();

                    if (assignDoc != null && assignDoc.exists()) {
                        Object groupsObj = assignDoc.get("groups");
                        if (groupsObj instanceof List) {
                            List<?> rawGroups = (List<?>) groupsObj;
                            for (Object gObj : rawGroups) {
                                if (gObj instanceof Map) {
                                    Map<?, ?> gm = (Map<?, ?>) gObj;
                                    Object nameObj = gm.get("groupName");
                                    String gName = nameObj instanceof String ? (String) nameObj : null;
                                    if (TextUtils.isEmpty(gName)) continue;

                                    Object membersObj = gm.get("memberUids");
                                    if (membersObj instanceof List) {
                                        List<?> membersRaw = (List<?>) membersObj;
                                        List<String> memberUids = new ArrayList<>();
                                        for (Object m : membersRaw) {
                                            if (m instanceof String) {
                                                String su = (String) m;
                                                if (!TextUtils.isEmpty(su)) {
                                                    memberUids.add(su);
                                                    studentGroupNameMap.put(su, gName);
                                                }
                                            }
                                        }
                                        if (!memberUids.isEmpty()) {
                                            groupNameToMembersMap.put(gName, memberUids);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2) Now load submissions collection
                    db.collection("assignments").document(assignmentId)
                            .collection("submissions")
                            .orderBy("submittedAt", Query.Direction.ASCENDING)
                            .get()
                            .addOnSuccessListener(qs -> {
                                list.clear();
                                for (DocumentSnapshot d : qs.getDocuments()) {
                                    SubmissionItem s = d.toObject(SubmissionItem.class);
                                    if (s == null) s = new SubmissionItem();
                                    s.setDocId(d.getId());
                                    if (TextUtils.isEmpty(s.getStudentUid())) {
                                        s.setStudentUid(d.getId());
                                    }
                                    list.add(s);
                                }

                                // 3) Sort: ungraded first, graded last; tie-breaker = submittedAt
                                Collections.sort(list, new Comparator<SubmissionItem>() {
                                    @Override
                                    public int compare(SubmissionItem a, SubmissionItem b) {
                                        boolean aGraded = "graded".equalsIgnoreCase(a.getStatus());
                                        boolean bGraded = "graded".equalsIgnoreCase(b.getStatus());
                                        if (aGraded != bGraded) {
                                            return aGraded ? 1 : -1;  // ungraded first
                                        }
                                        Long at = a.getSubmittedAt();
                                        Long bt = b.getSubmittedAt();
                                        if (at == null && bt == null) return 0;
                                        if (at == null) return 1;
                                        if (bt == null) return -1;
                                        return at.compareTo(bt);
                                    }
                                });

                                adapter.notifyDataSetChanged();
                                progress.dismiss();
                                if (list.isEmpty()) {
                                    Toast.makeText(this, "No submissions yet", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                progress.dismiss();
                                Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                Log.e(TAG, "loadSubmissions failed", e);
                            });

                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Failed to load assignment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "loadSubmissions: assignment load failed", e);
                });
    }

    // ------------------------ DATA CLASS ------------------------

    public static class SubmissionItem {
        String studentUid;
        String fileUrl;
        String fileName;
        String status;
        String grade;
        Double marks;
        String feedback;
        String gradedBy;
        Long gradedAt;
        Long submittedAt;
        String docId;

        public SubmissionItem() {}

        public String getStudentUid() { return studentUid; }
        public void setStudentUid(String s){ this.studentUid = s; }
        public String getFileUrl(){ return fileUrl; }
        public void setFileUrl(String u){ this.fileUrl = u; }
        public String getFileName(){ return fileName; }
        public void setFileName(String n){ this.fileName = n; }
        public String getStatus(){ return status; }
        public void setStatus(String st){ this.status = st; }
        public String getGrade(){ return grade; }
        public void setGrade(String g){ this.grade = g; }
        public Double getMarks(){ return marks; }
        public void setMarks(Double m){ this.marks = m; }
        public String getFeedback(){ return feedback; }
        public void setFeedback(String f){ this.feedback = f; }
        public String getGradedBy(){ return gradedBy; }
        public void setGradedBy(String gb){ this.gradedBy = gb; }
        public Long getGradedAt(){ return gradedAt; }
        public void setGradedAt(Long ga){ this.gradedAt = ga; }
        public Long getSubmittedAt(){ return submittedAt; }
        public void setSubmittedAt(Long s){ this.submittedAt = s; }
        public String getDocId(){ return docId; }
        public void setDocId(String d){ this.docId = d; }
    }

    // ------------------------ ADAPTER ------------------------

    class SubAdapter extends RecyclerView.Adapter<SubAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_submission, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SubmissionItem s = list.get(position);
            holder.bind(s);
        }

        @Override
        public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvFile, tvTime;
            Button btnOpen, btnDownload, btnGrade;

            VH(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvStudentName);
                tvFile = itemView.findViewById(R.id.tvSubmissionFile);
                tvTime = itemView.findViewById(R.id.tvSubmittedAt);

                btnOpen = itemView.findViewById(R.id.btnOpen);
                btnDownload = itemView.findViewById(R.id.btnDownload);
                btnGrade = itemView.findViewById(R.id.btnGrade);
            }

            void bind(SubmissionItem s) {
                // File name & timestamp
                tvFile.setText(!TextUtils.isEmpty(s.getFileName()) ? s.getFileName() : "(file)");
                if (s.getSubmittedAt() != null) {
                    String when = DateFormat.format("dd MMM yyyy, hh:mm a",
                            new Date(s.getSubmittedAt())).toString();
                    tvTime.setText(when);
                } else {
                    tvTime.setText("");
                }

                // Resolve student display name and append group name / size
                final String studentUid = s.getStudentUid();
                final String groupName = studentGroupNameMap.get(studentUid);
                final List<String> members = !TextUtils.isEmpty(groupName)
                        ? groupNameToMembersMap.get(groupName)
                        : null;

                if (!TextUtils.isEmpty(studentUid)) {
                    db.collection("users").document(studentUid).get()
                            .addOnSuccessListener(doc -> {
                                String label;
                                if (doc != null && doc.exists()) {
                                    String display = doc.getString("displayName");
                                    String email = doc.getString("email");
                                    label = !TextUtils.isEmpty(display) ? display :
                                            (!TextUtils.isEmpty(email) ? email : studentUid);
                                } else {
                                    label = studentUid;
                                }

                                // append group info
                                if (!TextUtils.isEmpty(groupName)) {
                                    int count = (members != null) ? members.size() : 0;
                                    if (count > 1) {
                                        label = label + "  •  " + groupName + " (" + count + " members)";
                                    } else {
                                        label = label + "  •  " + groupName;
                                    }
                                }
                                tvName.setText(label);
                            })
                            .addOnFailureListener(e -> {
                                String label = studentUid;
                                if (!TextUtils.isEmpty(groupName)) {
                                    int count = (members != null) ? members.size() : 0;
                                    if (count > 1) {
                                        label = label + "  •  " + groupName + " (" + count + " members)";
                                    } else {
                                        label = label + "  •  " + groupName;
                                    }
                                }
                                tvName.setText(label);
                            });
                } else {
                    String label = "(unknown student)";
                    if (!TextUtils.isEmpty(groupName)) {
                        int count = (members != null) ? members.size() : 0;
                        if (count > 1) {
                            label = label + "  •  " + groupName + " (" + count + " members)";
                        } else {
                            label = label + "  •  " + groupName;
                        }
                    }
                    tvName.setText(label);
                }

                // Open
                btnOpen.setOnClickListener(v -> {
                    if (!TextUtils.isEmpty(s.getFileUrl())) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(s.getFileUrl())));
                    } else {
                        Toast.makeText(TeacherSubmissionsActivity.this, "No file URL", Toast.LENGTH_SHORT).show();
                    }
                });

                // Download
                btnDownload.setOnClickListener(v -> {
                    if (TextUtils.isEmpty(s.getFileUrl())) {
                        Toast.makeText(TeacherSubmissionsActivity.this, "No file URL to download", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String filename = !TextUtils.isEmpty(s.getFileName())
                            ? s.getFileName()
                            : ("submission_" + (s.getSubmittedAt() != null ? s.getSubmittedAt() : System.currentTimeMillis()));
                    downloadRemoteFile(s.getFileUrl(), filename);
                });

                // Grade
                btnGrade.setOnClickListener(v -> openGradeDialog(s));

                // Row click → student's progress timeline
                itemView.setOnClickListener(v -> {
                    if (TextUtils.isEmpty(studentUid)) return;
                    Intent i = new Intent(v.getContext(), ProgressTimelineActivity.class);
                    i.putExtra(ProgressTimelineActivity.EXTRA_ASSIGNMENT_ID, assignmentId);
                    i.putExtra(ProgressTimelineActivity.EXTRA_STUDENT_UID, studentUid);
                    v.getContext().startActivity(i);
                });

                // Long-press → show all group members (names)
                itemView.setOnLongClickListener(v -> {
                    showGroupMembersDialogForStudent(studentUid);
                    return true;
                });
            }
        }
    }

    // ------------------------ DOWNLOAD ------------------------

    private void downloadRemoteFile(String url, String fileName) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                Toast.makeText(this, "Download manager unavailable", Toast.LENGTH_LONG).show();
                return;
            }
            Uri uri = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(fileName);
            request.setDescription("Downloading submission");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            dm.enqueue(request);
            Toast.makeText(this, "Download started — check Downloads", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "downloadRemoteFile failed", e);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------ GRADE DIALOG (WITH GROUP APPLY) ------------------------

    private void openGradeDialog(SubmissionItem s) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_grade, null, false);
        EditText etGrade = view.findViewById(R.id.etGrade);
        EditText etMarks = view.findViewById(R.id.etMarks);
        EditText etFeedback = view.findViewById(R.id.etFeedback);
        Button btnSave = view.findViewById(R.id.btnSaveGrade);

        if (!TextUtils.isEmpty(s.getGrade())) etGrade.setText(s.getGrade());
        if (s.getMarks() != null) etMarks.setText(String.valueOf(s.getMarks()));
        if (!TextUtils.isEmpty(s.getFeedback())) etFeedback.setText(s.getFeedback());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Grade " + (s.getFileName() != null ? s.getFileName() : "submission"))
                .setView(view)
                .setNegativeButton("Cancel", (d, which) -> {})
                .create();

        btnSave.setOnClickListener(v -> {
            String grade = etGrade.getText() != null ? etGrade.getText().toString().trim() : "";
            String marksStr = etMarks.getText() != null ? etMarks.getText().toString().trim() : "";
            String feedback = etFeedback.getText() != null ? etFeedback.getText().toString().trim() : "";

            Map<String,Object> upd = new HashMap<>();
            upd.put("grade", grade);
            if (!TextUtils.isEmpty(marksStr)) {
                try { upd.put("marks", Double.parseDouble(marksStr)); } catch (Exception ignored) {}
            }
            upd.put("feedback", feedback);
            upd.put("status", "graded");
            upd.put("gradedBy", mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null);
            upd.put("gradedAt", System.currentTimeMillis());

            if (s.getDocId() == null) {
                Toast.makeText(this, "Missing submission reference", Toast.LENGTH_LONG).show();
                return;
            }

            // Check if this student is in a group
            String groupName = studentGroupNameMap.get(s.getStudentUid());
            List<String> members = (!TextUtils.isEmpty(groupName))
                    ? groupNameToMembersMap.get(groupName)
                    : null;

            if (members == null || members.size() <= 1) {
                // No group / single member ⇒ normal single-student update
                applyGradeToSingleStudent(s, upd, dialog);
            } else {
                // Ask teacher: single vs whole group
                CharSequence[] options = new CharSequence[] {
                        "This student only",
                        "Whole group (" + members.size() + " students)"
                };
                new AlertDialog.Builder(this)
                        .setTitle("Apply grade to?")
                        .setItems(options, (d, which) -> {
                            if (which == 0) {
                                applyGradeToSingleStudent(s, upd, dialog);
                            } else {
                                applyGradeToGroup(s, members, upd, dialog);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        dialog.show();
    }

    private void applyGradeToSingleStudent(SubmissionItem s,
                                           Map<String,Object> upd,
                                           AlertDialog dialog) {
        db.collection("assignments").document(assignmentId)
                .collection("submissions").document(s.getDocId())
                .update(upd)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadSubmissions();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void applyGradeToGroup(SubmissionItem s,
                                   List<String> members,
                                   Map<String,Object> upd,
                                   AlertDialog dialog) {
        WriteBatch batch = db.batch();

        for (String memberUid : members) {
            DocumentReference ref = db.collection("assignments")
                    .document(assignmentId)
                    .collection("submissions")
                    .document(memberUid);

            Map<String,Object> body = new HashMap<>(upd);
            body.put("studentUid", memberUid);

            if (memberUid.equals(s.getStudentUid())) {
                // This is the one who actually submitted; update existing doc
                batch.update(ref, body);
            } else {
                // Other teammates: create/merge submission doc with grade
                // (even if they didn't upload a file themselves)
                batch.set(ref, body, SetOptions.merge());
            }
        }

        batch.commit()
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Grade applied to group", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadSubmissions();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Group grade failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ------------------------ GROUP MEMBERS DIALOG FOR TEACHER ------------------------

    private void showGroupMembersDialogForStudent(String studentUid) {
        if (TextUtils.isEmpty(studentUid)) {
            new AlertDialog.Builder(this)
                    .setTitle("Group members")
                    .setMessage("No student selected")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String groupName = studentGroupNameMap.get(studentUid);
        if (TextUtils.isEmpty(groupName)) {
            new AlertDialog.Builder(this)
                    .setTitle("Group members")
                    .setMessage("This student is not in a group for this assignment.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        List<String> members = groupNameToMembersMap.get(groupName);
        if (members == null || members.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Group: " + groupName)
                    .setMessage("No members found for this group.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        final int total = members.size();
        final int[] done = {0};

        for (String uid : members) {
            if (TextUtils.isEmpty(uid)) {
                done[0]++;
                if (done[0] == total) showTeacherGroupDialog(groupName, sb);
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
                        done[0]++;
                        if (done[0] == total) showTeacherGroupDialog(groupName, sb);
                    })
                    .addOnFailureListener(e -> {
                        sb.append(uid).append("\n");
                        done[0]++;
                        if (done[0] == total) showTeacherGroupDialog(groupName, sb);
                    });
        }
    }

    private void showTeacherGroupDialog(String groupName, StringBuilder sb) {
        String content = sb.length() == 0 ? "No members found" : sb.toString().trim();
        new AlertDialog.Builder(this)
                .setTitle("Group: " + groupName)
                .setMessage(content)
                .setPositiveButton("OK", null)
                .show();
    }
}
