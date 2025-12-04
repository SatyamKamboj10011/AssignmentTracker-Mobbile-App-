package com.satyam.assignmenttracker.models;

import java.util.List;

public class User {

    String id;
    String email;
    String displayName;
    String role;
    List<String> enrolledCourses;
    List<String> teacherCourses;

    public User(){

    }

    public User(String id, String email, String displayName, String role, List<String> enrolledCourses, List<String> teacherCourses) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.enrolledCourses = enrolledCourses;
        this.teacherCourses = teacherCourses;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getEnrolledCourses() {
        return enrolledCourses;
    }

    public void setEnrolledCourses(List<String> enrolledCourses) {
        this.enrolledCourses = enrolledCourses;
    }

    public List<String> getTeacherCourses() {
        return teacherCourses;
    }

    public void setTeacherCourses(List<String> teacherCourses) {
        this.teacherCourses = teacherCourses;
    }
}
