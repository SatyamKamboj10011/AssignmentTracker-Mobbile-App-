package com.satyam.assignmenttracker.Activities;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.CalendarView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.satyam.assignmenttracker.models.Assignment;
import com.satyam.assignmenttracker.Adapters.AssignmentAdapter;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class StudentCalendarActivity extends AppCompatActivity {

    private static final String TAG = "STU_CAL";

    private CalendarView calendarView;
    private TextView tvSelectedDate;
    private TextView tvEmptyDay;
    private RecyclerView rvDayAssignments;
    private AssignmentAdapter adapter;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();

    private String uid;

    private final List<Assignment> allAssignments = new ArrayList<>();
    private final List<Assignment> dayAssignments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_calendar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // Bind views
        ImageButton btnBack = findViewById(R.id.btnBack);
        calendarView = findViewById(R.id.calendarView);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvEmptyDay = findViewById(R.id.tvEmptyDay);
        rvDayAssignments = findViewById(R.id.rvDayAssignments);

        rvDayAssignments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AssignmentAdapter(this, dayAssignments);
        rvDayAssignments.setAdapter(adapter);

        btnBack.setOnClickListener(v -> onBackPressed());

        // Current user
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be signed in", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        uid = user.getUid();

        // Calendar date change listener
        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.YEAR, year);
                c.set(Calendar.MONTH, month);
                c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);

                long dayStart = c.getTimeInMillis();
                updateSelectedDateLabel(dayStart);
                filterAssignmentsForDay(dayStart);
            }
        });

        // Default selection: today
        long today = calendarView.getDate();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(today);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long todayStart = c.getTimeInMillis();

        updateSelectedDateLabel(todayStart);

        // Load assignments from Firestore
        loadAssignments();
    }

    private void updateSelectedDateLabel(long dayStartMillis) {
        CharSequence formatted = DateFormat.format("EEE, dd MMM yyyy", dayStartMillis);
        tvSelectedDate.setText("Assignments for " + formatted);
    }

    private void loadAssignments() {
        allAssignments.clear();
        dayAssignments.clear();
        adapter.notifyDataSetChanged();
        tvEmptyDay.setText("Loading assignments...");

        db.collection("assignments")
                .orderBy("dueDateMillis", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        try {
                            Assignment a = d.toObject(Assignment.class);
                            if (a == null) continue;
                            a.setId(d.getId());

                            // Check if this assignment is assigned to this student
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
                            }
                        } catch (Exception e) {
                            // ignore broken doc
                        }
                    }

                    // After loading all, filter for currently selected date
                    long selected = calendarView.getDate();
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(selected);
                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    long dayStart = c.getTimeInMillis();

                    filterAssignmentsForDay(dayStart);
                })
                .addOnFailureListener(e -> {
                    tvEmptyDay.setText("Failed to load assignments: " + e.getMessage());
                    Toast.makeText(this, "Error loading assignments", Toast.LENGTH_LONG).show();
                });
    }

    private void filterAssignmentsForDay(long dayStartMillis) {
        dayAssignments.clear();

        long dayEnd = dayStartMillis + 24L * 60L * 60L * 1000L;

        for (Assignment a : allAssignments) {
            Long dueMillis = a.getDueDateMillis();
            if (dueMillis == null) continue;

            if (dueMillis >= dayStartMillis && dueMillis < dayEnd) {
                dayAssignments.add(a);
            }
        }

        adapter.notifyDataSetChanged();

        if (dayAssignments.isEmpty()) {
            tvEmptyDay.setVisibility(View.VISIBLE);
            tvEmptyDay.setText("No assignments due on this date.");
        } else {
            tvEmptyDay.setVisibility(View.GONE);
        }
    }
}
