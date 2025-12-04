package com.satyam.assignmenttracker.models;
import java.util.List;
import java.util.Map;

public class Assignment {

    private String id;
    private String title;
    private String description;
    private String dueDate;
    private Long dueDateMillis;
    private String courseId;
    private String createdBy;
    private Long timestamp;
    private Boolean assignToAll;
    private Map<String, List<String>> groupMap;
    private List<String> assignedTo;  // ← THIS IS THE MISSING FIELD

    // --------- GETTERS & SETTERS ---------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public Long getDueDateMillis() { return dueDateMillis; }
    public void setDueDateMillis(Long dueDateMillis) { this.dueDateMillis = dueDateMillis; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public Boolean isAssignToAll() { return assignToAll; }
    public void setAssignToAll(Boolean assignToAll) { this.assignToAll = assignToAll; }

    public List<String> getAssignedTo() { return assignedTo; }          // ← MISSING GETTER
    public void setAssignedTo(List<String> assignedTo) {               // ← MISSING SETTER
        this.assignedTo = assignedTo;
    }

    public Map<String, List<String>> getGroupMap() {
        return groupMap;
    }

    public void setGroupMap(Map<String, List<String>> groupMap) {
        this.groupMap = groupMap;
    }

}
