package com.satyam.assignmenttracker.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "draft_submissions")
public class DraftSubmissionEntity {

    @PrimaryKey
    @NonNull
    public String id;              // assignmentId + "_" + userId

    public String assignmentId;
    public String userId;

    public String fileUri;         // Uri.toString()
    public String fileName;
    public long fileSize;
    public String mimeType;

    public long lastEditedAt;
}
