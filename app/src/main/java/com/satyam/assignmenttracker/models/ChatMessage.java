package com.satyam.assignmenttracker.models;

public class ChatMessage {

    private String text;
    private boolean isUser;
    private long timestamp;

    // --------- CONSTRUCTORS ---------

    // 1) Default constructor for Firebase / serialization
    public ChatMessage() {
    }

    // 2) Constructor used when sending messages in the app
    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis(); // auto timestamp
    }

    // 3) Full constructor if needed explicitly
    public ChatMessage(String text, boolean isUser, long timestamp) {
        this.text = text;
        this.isUser = isUser;
        this.timestamp = timestamp;
    }

    // --------- GETTERS & SETTERS ---------

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean user) {
        isUser = user;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
