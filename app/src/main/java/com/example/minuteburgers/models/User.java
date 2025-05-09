package com.example.minuteburgers.models;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

public class User {
    private String uid;
    private String name;
    private String email;
    private String role; // "owner" or "employee"
    private String branch;
    private Date registrationDate;
    private String profileImageUrl;
    private String additionalInfo; // Additional information for email templates

    // Required empty constructor for Firebase
    public User() {
    }

    public User(String uid, String name, String email, String role, String branch) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.branch = branch;
        this.registrationDate = new Date();
    }

    @Exclude
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    @Exclude
    public boolean isOwner() {
        return "owner".equalsIgnoreCase(role);
    }

    @Exclude
    public boolean isEmployee() {
        return "employee".equalsIgnoreCase(role);
    }

    @Exclude
    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }
}
