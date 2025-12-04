package com.satyam.assignmenttracker.models;

public class CourseSummary {
    public String courseId;
    public String title;
    public int count;
    public String nextDueText;

    public CourseSummary(String courseId, String title, int count, String nextDueText) {
        this.courseId = courseId;
        this.title = title;
        this.count = count;
        this.nextDueText = nextDueText;
    }
}
