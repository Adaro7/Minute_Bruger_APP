package com.example.minuteburgers.models;

import com.google.firebase.database.Exclude;

import java.util.Date;

/**
 * Model class for sales records
 */
public class SalesRecord {
    private String id;
    private String itemId;
    private String itemName;
    private int quantity;
    private double unitPrice;
    private double totalAmount;
    private String soldBy; // User ID
    private String soldByName; // User name
    private String branch;
    private Date saleDate;
    private String notes;
    
    // Required empty constructor for Firebase
    public SalesRecord() {
        this.saleDate = new Date();
    }
    
    /**
     * Constructor for creating a new sales record
     */
    public SalesRecord(String itemId, String itemName, int quantity, double unitPrice, 
                      String soldBy, String soldByName, String branch) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = quantity * unitPrice;
        this.soldBy = soldBy;
        this.soldByName = soldByName;
        this.branch = branch;
        this.saleDate = new Date();
    }
    
    @Exclude
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getItemId() {
        return itemId;
    }
    
    public void setItemId(String itemId) {
        this.itemId = itemId;
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
        // Recalculate total amount when quantity changes
        this.totalAmount = this.quantity * this.unitPrice;
    }
    
    public double getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        // Recalculate total amount when unit price changes
        this.totalAmount = this.quantity * this.unitPrice;
    }
    
    public double getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public String getSoldBy() {
        return soldBy;
    }
    
    public void setSoldBy(String soldBy) {
        this.soldBy = soldBy;
    }
    
    public String getSoldByName() {
        return soldByName;
    }
    
    public void setSoldByName(String soldByName) {
        this.soldByName = soldByName;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public Date getSaleDate() {
        return saleDate;
    }
    
    public void setSaleDate(Date saleDate) {
        this.saleDate = saleDate;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
}
