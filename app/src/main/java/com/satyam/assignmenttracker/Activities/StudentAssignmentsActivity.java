package com.satyam.assignmenttracker.Activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Assignment;
import com.satyam.assignmenttracker.Adapters.AssignmentAdapter;
import com.satyam.assignmenttracker.fb.AssignmentReminderScheduler;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class StudentAssignmentsActivity extends AppCompatActivity {

    private static final String TAG = "STU_ASSIGN";

    RecyclerView rv;
    AssignmentAdapter adapter;

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    // All assignments the student can see
    List<Assignment> allAssignments = new ArrayList<>();
    // Only assignments the student has submitted
    List<Assignment> submittedAssignments = new ArrayList<>();
    // Currently displayed list (used by adapter)
    List<Assignment> filteredAssignments = new ArrayList<>();

    SwipeRefreshLayout swipeRefresh;
    EditText etSearch;
    LinearLayout emptyState;

    Button btnRefresh, btnMySubmissions;

    // current user id
    String uid;
    // optional course filter passed via intent
    String courseIdFilter;

    // Are we currently showing only "my submissions"?
    boolean showingMySubmissions = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_assignments);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        rv = findViewById(R.id.rvAssignments);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etSearch = findViewById(R.id.etSearch);
        emptyState = findViewById(R.id.emptyState);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnMySubmissions = findViewById(R.id.btnMySubmissions);

        adapter = new AssignmentAdapter(this, filteredAssignments);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // get current user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be signed in to view assignments", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        uid = user.getUid();

        // optional course filter
        courseIdFilter = getIntent().getStringExtra("courseId");

        // wire UI
        loadAssignments();

        // Pull-to-refresh (always resets back to "All Assignments")
        swipeRefresh.setOnRefreshListener(() -> {
            showingMySubmissions = false;
            btnMySubmissions.setText("My Submissions");
            loadAssignments();
        });

        // Button refresh (same as pull-to-refresh)
        btnRefresh.setOnClickListener(v -> {
            showingMySubmissions = false;
            btnMySubmissions.setText("My Submissions");
            loadAssignments();
        });

        // "My Submissions" toggle
        btnMySubmissions.setOnClickListener(v -> {
            if (!showingMySubmissions) {
                // switch to show only submitted assignments
                showingMySubmissions = true;
                btnMySubmissions.setText("All Assignments");
                loadSubmittedAssignments();
            } else {
                // switch back to all assignments
                showingMySubmissions = false;
                btnMySubmissions.setText("My Submissions");
                // base list = allAssignments
                filteredAssignments.clear();
                filteredAssignments.addAll(allAssignments);
                adapter.notifyDataSetChanged();
                emptyState.setVisibility(filteredAssignments.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        // Search filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAssignments(s.toString().trim());
            }
        });
    }

    /**
     * Load assignments, but filter to:
     * - assignments for the provided courseId (if present), OR
     * - assignments whose courseId is in the student's enrolledCourses.
     *
     * AND always only those assignments that are assigned to this student:
     * - assignToAll == true OR assignedTo contains uid
     */
    private void loadAssignments() {
        swipeRefresh.setRefreshing(true);
        emptyState.setVisibility(View.GONE);

        allAssignments.clear();
        submittedAssignments.clear();
        filteredAssignments.clear();
        adapter.notifyDataSetChanged();

        Log.d(TAG, "Fetching assignments for student uid=" + uid + " courseFilter=" + courseIdFilter);

        // First, load student's enrolledCourses (unless a courseId filter was passed)
        if (courseIdFilter == null || courseIdFilter.trim().isEmpty()) {
            // need enrolled courses to decide which assignments to show
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(userDoc -> {
                        List<String> enrolled = (List<String>) userDoc.get("enrolledCourses");
                        Set<String> enrolledSet = new HashSet<>();
                        if (enrolled != null) {
                            for (String c : enrolled) if (c != null) enrolledSet.add(c);
                        }
                        // proceed to load assignments and filter client-side
                        fetchAndFilterAssignments(enrolledSet);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to load user enrolledCourses: " + e.getMessage());
                        // fallback: treat as no enrolled courses -> show none
                        swipeRefresh.setRefreshing(false);
                        emptyState.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "Failed to load enrolled courses", Toast.LENGTH_LONG).show();
                    });
        } else {
            // course filter provided; we only need to filter by courseIdFilter and assignedTo/assignToAll
            fetchAndFilterAssignments(null);
        }
    }

    /**
     * Fetch assignments from Firestore and apply client-side filtering.
     *
     * @param enrolledSet if non-null => only assignments whose courseId is in this set will be considered.
     *                    If null => do not restrict by enrolled courses (we assume courseIdFilter is used).
     */
    private void fetchAndFilterAssignments(Set<String> enrolledSet) {
        Query q = db.collection("assignments").orderBy("dueDateMillis", Query.Direction.ASCENDING);

        // If courseIdFilter present, we can narrow results server-side by adding whereEqualTo
        if (courseIdFilter != null && !courseIdFilter.trim().isEmpty()) {
            q = db.collection("assignments")
                    .whereEqualTo("courseId", courseIdFilter)
                    .orderBy("dueDateMillis", Query.Direction.ASCENDING);
        }

        q.get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        try {
                            Assignment a = d.toObject(Assignment.class);
                            if (a == null) continue;
                            a.setId(d.getId());

                            // Check course membership if enrolledSet is provided
                            if (enrolledSet != null) {
                                String courseId = a.getCourseId();
                                if (courseId == null || !enrolledSet.contains(courseId)) {
                                    // student not enrolled in this assignment's course -> skip
                                    continue;
                                }
                            }

                            // Now check whether the assignment is assigned to this student
                            Boolean assignToAll = d.getBoolean("assignToAll");
                            if (assignToAll == null) assignToAll = Boolean.FALSE;

                            boolean isAssigned = false;
                            if (Boolean.TRUE.equals(assignToAll)) {
                                isAssigned = true;
                            } else {
                                List<String> assignedTo = (List<String>) d.get("assignedTo");
                                if (assignedTo != null && assignedTo.contains(uid)) {
                                    isAssigned = true;
                                }
                            }

                            if (isAssigned) {
                                allAssignments.add(a);

                                // 🔔 schedule reminder for this assignment
                                Long dueMillis = a.getDueDateMillis();
                                String title = a.getTitle();

                                if (dueMillis != null) {
                                    AssignmentReminderScheduler.scheduleReminder(
                                            StudentAssignmentsActivity.this,
                                            a.getId(),
                                            title != null ? title : "Assignment",
                                            dueMillis
                                    );
                                }
                            }



                        } catch (Exception ex) {
                            Log.e(TAG, "Error parsing/filtering assignment: " + ex.getMessage());
                        }
                    }

                    // after loading, by default show ALL assignments
                    showingMySubmissions = false;
                    btnMySubmissions.setText("My Submissions");

                    filteredAssignments.clear();
                    filteredAssignments.addAll(allAssignments);
                    adapter.notifyDataSetChanged();

                    swipeRefresh.setRefreshing(false);
                    emptyState.setVisibility(filteredAssignments.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load assignments: " + e.getMessage());
                    swipeRefresh.setRefreshing(false);
                    emptyState.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Error loading assignments: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Load only assignments that this student has submitted.
     * Uses allAssignments as base and checks submissions subcollection for each.
     */
    private void loadSubmittedAssignments() {
        swipeRefresh.setRefreshing(true);
        emptyState.setVisibility(View.GONE);

        submittedAssignments.clear();
        filteredAssignments.clear();
        adapter.notifyDataSetChanged();

        if (allAssignments.isEmpty()) {
            // nothing loaded, nothing submitted
            swipeRefresh.setRefreshing(false);
            emptyState.setVisibility(View.VISIBLE);
            Toast.makeText(this, "No assignments available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(allAssignments.size());

        for (Assignment a : allAssignments) {
            String assignmentId = a.getId();
            if (assignmentId == null) {
                if (remaining.decrementAndGet() == 0) finishSubmittedFilter();
                continue;
            }

            db.collection("assignments").document(assignmentId)
                    .collection("submissions")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc != null && doc.exists()) {
                            submittedAssignments.add(a);
                        }
                        if (remaining.decrementAndGet() == 0) finishSubmittedFilter();
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "check submission failed for assignment=" + assignmentId + ": " + e.getMessage());
                        if (remaining.decrementAndGet() == 0) finishSubmittedFilter();
                    });
        }
    }

    private void finishSubmittedFilter() {
        swipeRefresh.setRefreshing(false);

        // base list now = submittedAssignments
        filteredAssignments.clear();
        filteredAssignments.addAll(submittedAssignments);
        adapter.notifyDataSetChanged();

        emptyState.setVisibility(filteredAssignments.isEmpty() ? View.VISIBLE : View.GONE);

        if (filteredAssignments.isEmpty()) {
            Toast.makeText(this, "You haven't submitted any assignments yet.", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterAssignments(String query) {
        List<Assignment> source;

        // If we're in "My Submissions" mode, search within submittedAssignments.
        // Otherwise, search within allAssignments.
        if (showingMySubmissions) {
            source = submittedAssignments;
        } else {
            source = allAssignments;
        }

        filteredAssignments.clear();

        if (query.isEmpty()) {
            filteredAssignments.addAll(source);
        } else {
            String q = query.toLowerCase(Locale.ROOT);
            for (Assignment a : source) {
                if (a.getTitle() != null && a.getTitle().toLowerCase(Locale.ROOT).contains(q)) {
                    filteredAssignments.add(a);
                }
            }
        }

        emptyState.setVisibility(filteredAssignments.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }
}
