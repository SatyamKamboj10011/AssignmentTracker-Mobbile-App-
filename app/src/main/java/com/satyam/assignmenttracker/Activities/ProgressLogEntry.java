package com.satyam.assignmenttracker.Activities;

public class ProgressLogEntry {
    private String id;
    private String studentUid;
    private String studentEmail;
    private String assignmentId;
    private String notes;
    private boolean stepRead;
    private boolean stepDraft;
    private boolean stepFinal;
    private long timestamp;
    private String dateFormatted;

    public ProgressLogEntry() {
        // Firestore needs empty constructor
    }

    // getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStudentUid() { return studentUid; }
    public void setStudentUid(String studentUid) { this.studentUid = studentUid; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isStepRead() { return stepRead; }
    public void setStepRead(boolean stepRead) { this.stepRead = stepRead; }

    public boolean isStepDraft() { return stepDraft; }
    public void setStepDraft(boolean stepDraft) { this.stepDraft = stepDraft; }

    public boolean isStepFinal() { return stepFinal; }
    public void setStepFinal(boolean stepFinal) { this.stepFinal = stepFinal; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getDateFormatted() { return dateFormatted; }
    public void setDateFormatted(String dateFormatted) { this.dateFormatted = dateFormatted; }
}
