package com.satyam.assignmenttracker.Activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Base;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProgressTimelineActivity extends Base {

    // 🔹 Keys used by BOTH student side & TeacherSubmissionsActivity
    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";
    public static final String EXTRA_STUDENT_UID   = "studentUid";
    public static final String EXTRA_STUDENT_NAME  = "studentName";

    private RecyclerView rvTimeline;
    private TextView tvEmpty, tvTitle;
    private ImageButton btnBack;

    private TimelineAdapter adapter;
    private final List<TimelineItem> items = new ArrayList<>();

    private String assignmentId;
    private String studentUid;
    private String studentName;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress_timeline);

        rvTimeline = findViewById(R.id.rvTimeline);
        tvEmpty    = findViewById(R.id.tvEmpty);
        tvTitle    = findViewById(R.id.tvHeaderTitle);
        btnBack    = findViewById(R.id.btnBackTimeline);

        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        // Read extras using the same keys TeacherSubmissionsActivity sends
        assignmentId = getIntent().getStringExtra(EXTRA_ASSIGNMENT_ID);
        studentUid   = getIntent().getStringExtra(EXTRA_STUDENT_UID);
        studentName  = getIntent().getStringExtra(EXTRA_STUDENT_NAME);

        // If studentUid missing and this is a student opening their own timeline, fallback
        if (TextUtils.isEmpty(studentUid) && mAuth.getCurrentUser() != null) {
            studentUid = mAuth.getCurrentUser().getUid();
        }

        if (TextUtils.isEmpty(assignmentId) || TextUtils.isEmpty(studentUid)) {
            Toast.makeText(this, "Missing assignment or student", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Header title:
        //  - teacher view: "Progress — <Student Name>"
        //  - student view (no name passed): "My Progress Timeline"
        if (tvTitle != null) {
            if (!TextUtils.isEmpty(studentName)) {
                tvTitle.setText("Progress — " + studentName);
            } else {
                tvTitle.setText("My Progress Timeline");
            }
        }

        adapter = new TimelineAdapter(items);
        rvTimeline.setLayoutManager(new LinearLayoutManager(this));
        rvTimeline.setAdapter(adapter);

        loadTimeline();
    }

    private void loadTimeline() {
        db.collection("assignments")
                .document(assignmentId)
                .collection("submissions")
                .document(studentUid)
                .collection("progressLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    items.clear();
                    if (qs.isEmpty()) {
                        updateEmptyState(true);
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        String text = doc.getString("text");
                        Object tsObj = doc.get("createdAt");
                        long createdAt = 0L;
                        if (tsObj instanceof Number) {
                            createdAt = ((Number) tsObj).longValue();
                        }

                        String dateStr = createdAt > 0
                                ? DateFormat.format("dd MMM yyyy, hh:mm a",
                                new Date(createdAt)).toString()
                                : "--";

                        if (!TextUtils.isEmpty(text)) {
                            items.add(new TimelineItem(dateStr, text));
                        }
                    }

                    adapter.notifyDataSetChanged();
                    updateEmptyState(items.isEmpty());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load progress: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateEmptyState(true);
                });
    }

    private void updateEmptyState(boolean empty) {
        if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvTimeline != null) rvTimeline.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ---- model + adapter ----

    static class TimelineItem {
        final String date;
        final String text;

        TimelineItem(String date, String text) {
            this.date = date;
            this.text = text;
        }
    }

    static class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineVH> {

        private final List<TimelineItem> data;

        TimelineAdapter(List<TimelineItem> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public TimelineVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_progress_entry, parent, false);
            return new TimelineVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull TimelineVH holder, int position) {
            TimelineItem item = data.get(position);
            holder.tvDate.setText(item.date);
            holder.tvText.setText(item.text);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class TimelineVH extends RecyclerView.ViewHolder {
            TextView tvDate, tvText;

            TimelineVH(@NonNull android.view.View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvEntryDate);
                tvText = itemView.findViewById(R.id.tvEntryNotes);
                // The checkboxes from item_progress_entry are just indicators. We don't touch them here.
            }
        }
    }
}
