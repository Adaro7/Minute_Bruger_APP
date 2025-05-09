package com.example.minuteburgers;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class InventoryItem {
    private String id;
    private String name;
    private int quantity;
    private String imageUrl;
    private String category;
    private String branch; // Added branch field
    private long lastUpdated;
    private String lastUpdatedBy;
    private List<ItemHistory> history;

    // Required empty constructor for Firebase
    public InventoryItem() {
        this.history = new ArrayList<>();
    }

    public InventoryItem(String name, int quantity, String category) {
        this.name = name;
        this.quantity = quantity;
        this.category = category;
        this.branch = "All Branches"; // Default branch
        this.history = new ArrayList<>();
        this.lastUpdated = System.currentTimeMillis();
    }

    public InventoryItem(String name, int quantity, String category, String branch) {
        this.name = name;
        this.quantity = quantity;
        this.category = category;
        this.branch = branch;
        this.history = new ArrayList<>();
        this.lastUpdated = System.currentTimeMillis();
    }

    @Exclude
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public List<ItemHistory> getHistory() {
        return history;
    }

    public void setHistory(List<ItemHistory> history) {
        this.history = history;
    }

    public void addHistoryEntry(String action, String user, String details) {
        ItemHistory historyEntry = new ItemHistory(action, user, details, System.currentTimeMillis());
        this.history.add(historyEntry);
    }

    public static class ItemHistory {
        private String action;
        private String user;
        private String details;
        private long timestamp;

        public ItemHistory() {}

        public ItemHistory(String action, String user, String details, long timestamp) {
            this.action = action;
            this.user = user;
            this.details = details;
            this.timestamp = timestamp;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}