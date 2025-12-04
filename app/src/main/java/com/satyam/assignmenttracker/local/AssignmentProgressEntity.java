package com.satyam.assignmenttracker.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "assignment_progress")
public class AssignmentProgressEntity {

    @PrimaryKey
    @NonNull
    public String id;            // assignmentId + "_" + userId

    public String assignmentId;
    public String userId;

    public boolean stepReadMaterial;   // "Read materials / instructions"
    public boolean stepDraftOutline;   // "Draft outline"
    public boolean stepFinalWrite;     // "Final version done"

    public String notes;               // student's own notes

    public long updatedAt;
}
