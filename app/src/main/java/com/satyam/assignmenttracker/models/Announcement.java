package com.satyam.assignmenttracker.models;

public class Announcement {

    private String id;
    private String title;
    private String message;
    private Long createdAt;      // millis
    private String courseId;
    private String courseTitle;
    private String createdBy;
    private String creatorName;

    public Announcement() {
        // Firestore needs empty constructor
    }

    public Announcement(String id, String title, String message,
                        Long createdAt, String courseId, String courseTitle,
                        String createdBy, String creatorName) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.createdBy = createdBy;
        this.creatorName = creatorName;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getCreatorName() {
        return creatorName;
    }
}
