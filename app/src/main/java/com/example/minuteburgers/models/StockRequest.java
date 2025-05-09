package com.example.minuteburgers.models;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

public class StockRequest {
    private String id;
    private String itemName;
    private int quantity;
    private String requestedBy; // User ID
    private String requestedByName; // User name
    private String requestedByEmail; // User email
    private String branch;
    private Date requestDate;
    private String status; // "pending", "approved", "rejected"
    private String notes;
    private Date responseDate;
    private String respondedBy; // Owner ID

    // Required empty constructor for Firebase
    public StockRequest() {
        this.requestDate = new Date();
        this.status = "pending";
    }

    public StockRequest(String itemName, int quantity, String requestedBy, 
                        String requestedByName, String requestedByEmail, String branch) {
        this.itemName = itemName;
        this.quantity = quantity;
        this.requestedBy = requestedBy;
        this.requestedByName = requestedByName;
        this.requestedByEmail = requestedByEmail;
        this.branch = branch;
        this.requestDate = new Date();
        this.status = "pending";
    }

    @Exclude
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public void setRequestedByName(String requestedByName) {
        this.requestedByName = requestedByName;
    }

    public String getRequestedByEmail() {
        return requestedByEmail;
    }

    public void setRequestedByEmail(String requestedByEmail) {
        this.requestedByEmail = requestedByEmail;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Date getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Date getResponseDate() {
        return responseDate;
    }

    public void setResponseDate(Date responseDate) {
        this.responseDate = responseDate;
    }

    public String getRespondedBy() {
        return respondedBy;
    }

    public void setRespondedBy(String respondedBy) {
        this.respondedBy = respondedBy;
    }

    @Exclude
    public boolean isPending() {
        return "pending".equalsIgnoreCase(status);
    }

    @Exclude
    public boolean isApproved() {
        return "approved".equalsIgnoreCase(status);
    }

    @Exclude
    public boolean isRejected() {
        return "rejected".equalsIgnoreCase(status);
    }
}
