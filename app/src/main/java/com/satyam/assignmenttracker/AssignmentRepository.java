package com.satyam.assignmenttracker;

import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.models.Assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory repository for chat:
 * - Loads assignments assigned to the current student
 * - Optionally marks those with submissions as "completed"
 */
public class AssignmentRepository {

    private static final String TAG = "AssignmentRepository";

    private static final List<Assignment> cachedAssignments = new ArrayList<>();
    private static final Set<String> completedAssignmentIds = new HashSet<>();
    private static boolean initialized = false;

    public interface InitCallback {
        void onReady();
        void onError(String message);
    }

    /**
     * Load assignments + completion info for a given student UID.
     * Call this once (e.g. in ChatActivity.onCreate).
     */
    public static void initForStudent(FirebaseFirestore db, String uid, InitCallback cb) {
        initialized = false;
        cachedAssignments.clear();
        completedAssignmentIds.clear();

        db.collection("assignments")
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) {
                        initialized = true;
                        if (cb != null) cb.onReady();
                        return;
                    }

                    List<DocumentSnapshot> docs = qs.getDocuments();
                    AtomicInteger pending = new AtomicInteger(docs.size());

                    for (DocumentSnapshot d : docs) {
                        Assignment a = d.toObject(Assignment.class);
                        if (a == null) {
                            if (pending.decrementAndGet() == 0) {
                                finishInit(cb);
                            }
                            continue;
                        }

                        a.setId(d.getId());

                        // Filter: only assignments assigned to this student
                        Boolean assignToAll = d.getBoolean("assignToAll");
                        if (assignToAll == null) assignToAll = Boolean.FALSE;
                        List<String> assignedTo = (List<String>) d.get("assignedTo");

                        boolean isAssigned = assignToAll
                                || (assignedTo != null && assignedTo.contains(uid));

                        if (!isAssigned) {
                            if (pending.decrementAndGet() == 0) {
                                finishInit(cb);
                            }
                            continue;
                        }

                        cachedAssignments.add(a);

                        // For completion: check submissions/{uid}
                        d.getReference()
                                .collection("submissions")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(sub -> {
                                    if (sub != null && sub.exists()) {
                                        String status = sub.getString("status");
                                        if (status != null &&
                                                ("submitted".equalsIgnoreCase(status)
                                                        || "graded".equalsIgnoreCase(status))) {
                                            completedAssignmentIds.add(a.getId());
                                        }
                                    }
                                    if (pending.decrementAndGet() == 0) {
                                        finishInit(cb);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "submissions read failed: " + e.getMessage());
                                    if (pending.decrementAndGet() == 0) {
                                        finishInit(cb);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "initForStudent failed", e);
                    if (cb != null) cb.onError(e.getMessage());
                });
    }

    private static void finishInit(InitCallback cb) {
        initialized = true;
        sortByDueDate();
        if (cb != null) cb.onReady();
    }

    private static void sortByDueDate() {
        Collections.sort(cachedAssignments, new Comparator<Assignment>() {
            @Override
            public int compare(Assignment a1, Assignment a2) {
                Long d1 = a1.getDueDateMillis();
                Long d2 = a2.getDueDateMillis();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return Long.compare(d1, d2);
            }
        });
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static List<String> getCompletedIds() {
        return new ArrayList<>(completedAssignmentIds);
    }

    public static List<Assignment> getAllAssignments() {
        return new ArrayList<>(cachedAssignments);
    }

    public static Assignment getNextDue(long now, List<String> completedIds) {
        if (!initialized) return null;
        Set<String> completed = completedIds != null
                ? new HashSet<>(completedIds)
                : completedAssignmentIds;

        Assignment best = null;
        long bestTime = Long.MAX_VALUE;

        for (Assignment a : cachedAssignments) {
            Long dueMillis = a.getDueDateMillis();
            if (dueMillis == null) continue;
            if (dueMillis < now) continue;                    // only future
            if (completed.contains(a.getId())) continue;       // skip completed
            if (dueMillis < bestTime) {
                best = a;
                bestTime = dueMillis;
            }
        }
        return best;
    }

    public static List<Assignment> getOverdue(long now, List<String> completedIds) {
        if (!initialized) return new ArrayList<>();
        Set<String> completed = completedIds != null
                ? new HashSet<>(completedIds)
                : completedAssignmentIds;

        List<Assignment> list = new ArrayList<>();
        for (Assignment a : cachedAssignments) {
            Long dueMillis = a.getDueDateMillis();
            if (dueMillis == null) continue;
            if (dueMillis >= now) continue;
            if (completed.contains(a.getId())) continue;
            list.add(a);
        }
        return list;
    }

    public static List<Assignment> getPendingAssignments(long now, List<String> completedIds) {
        if (!initialized) return new ArrayList<>();
        Set<String> completed = completedIds != null
                ? new HashSet<>(completedIds)
                : completedAssignmentIds;

        List<Assignment> list = new ArrayList<>();
        for (Assignment a : cachedAssignments) {
            if (completed.contains(a.getId())) continue;
            list.add(a);
        }
        return list;
    }

    public static List<Assignment> getByCourse(String courseQuery) {
        if (!initialized) return new ArrayList<>();
        if (courseQuery == null) return new ArrayList<>();

        String q = courseQuery.trim().toLowerCase();
        List<Assignment> list = new ArrayList<>();

        for (Assignment a : cachedAssignments) {
            String cid = a.getCourseId();
            if (cid != null && cid.toLowerCase().contains(q)) {
                list.add(a);
            }
        }
        return list;
    }
}
