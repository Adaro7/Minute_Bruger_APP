package com.example.minuteburgers.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.minuteburgers.EmployeeDashboardActivity;
import com.example.minuteburgers.LoginActivity;
import com.example.minuteburgers.OwnerDashboardActivity;
import com.example.minuteburgers.R;
import com.example.minuteburgers.models.StockRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Helper class for managing notifications
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    
    // Notification channel IDs
    public static final String CHANNEL_STOCK_REQUESTS = "stock_requests";
    public static final String CHANNEL_INVENTORY = "inventory";
    public static final String CHANNEL_SALES = "sales";
    
    // Notification IDs
    private static final int NOTIFICATION_ID_STOCK_REQUEST = 1001;
    private static final int NOTIFICATION_ID_LOW_INVENTORY = 1002;
    private static final int NOTIFICATION_ID_SALES = 1003;
    
    private final Context context;
    
    public NotificationHelper(Context context) {
        this.context = context;
        createNotificationChannels();
    }
    
    /**
     * Create notification channels for Android O and above
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Stock requests channel
            NotificationChannel stockRequestsChannel = new NotificationChannel(
                    CHANNEL_STOCK_REQUESTS,
                    "Stock Requests",
                    NotificationManager.IMPORTANCE_HIGH);
            stockRequestsChannel.setDescription("Notifications for stock requests");
            
            // Inventory channel
            NotificationChannel inventoryChannel = new NotificationChannel(
                    CHANNEL_INVENTORY,
                    "Inventory",
                    NotificationManager.IMPORTANCE_DEFAULT);
            inventoryChannel.setDescription("Notifications for inventory updates");
            
            // Sales channel
            NotificationChannel salesChannel = new NotificationChannel(
                    CHANNEL_SALES,
                    "Sales",
                    NotificationManager.IMPORTANCE_DEFAULT);
            salesChannel.setDescription("Notifications for sales and reports");
            
            // Register the channels
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(stockRequestsChannel);
                notificationManager.createNotificationChannel(inventoryChannel);
                notificationManager.createNotificationChannel(salesChannel);
            }
        }
    }
    
    /**
     * Send a notification to the employee about their stock request status
     */
    public void sendStockRequestStatusNotification(StockRequest request) {
        String title;
        String message;
        
        if (request.isApproved()) {
            title = "Stock Request Approved";
            message = "Your request for " + request.getQuantity() + " " + request.getItemName() + " has been approved.";
        } else if (request.isRejected()) {
            title = "Stock Request Rejected";
            message = "Your request for " + request.getQuantity() + " " + request.getItemName() + " has been rejected.";
        } else {
            return; // Don't send notification for pending requests
        }
        
        // Create an intent to open the employee dashboard
        Intent intent = new Intent(context, EmployeeDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
                PendingIntent.FLAG_IMMUTABLE);
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_STOCK_REQUESTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        // Show the notification
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(NOTIFICATION_ID_STOCK_REQUEST, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for showing notification", e);
        }
        
        // Also send an email notification
        sendStockRequestStatusEmail(request);
    }
    
    /**
     * Send an email notification to the employee about their stock request status
     */
    private void sendStockRequestStatusEmail(StockRequest request) {
        String subject;
        String message;
        
        if (request.isApproved()) {
            subject = "Stock Request Approved - " + request.getItemName();
            message = "Hello " + request.getRequestedByName() + ",\n\n" +
                    "Your stock request has been APPROVED.\n\n" +
                    "Item: " + request.getItemName() + "\n" +
                    "Quantity: " + request.getQuantity() + "\n" +
                    "Branch: " + request.getBranch() + "\n" +
                    "Request Date: " + request.getRequestDate() + "\n\n" +
                    "The requested items will be delivered to your branch soon.\n\n" +
                    "Regards,\nMinute Burgers Management";
        } else if (request.isRejected()) {
            subject = "Stock Request Rejected - " + request.getItemName();
            message = "Hello " + request.getRequestedByName() + ",\n\n" +
                    "Your stock request has been REJECTED.\n\n" +
                    "Item: " + request.getItemName() + "\n" +
                    "Quantity: " + request.getQuantity() + "\n" +
                    "Branch: " + request.getBranch() + "\n" +
                    "Request Date: " + request.getRequestDate() + "\n\n" +
                    "Please contact management for more information.\n\n" +
                    "Regards,\nMinute Burgers Management";
        } else {
            return; // Don't send email for pending requests
        }
        
        // Send the email
        EmailSender.sendEmail(context, request.getRequestedByEmail(), subject, message);
    }
    
    /**
     * Send a notification about low inventory
     */
    public void sendLowInventoryNotification(String itemName, int quantity) {
        String title = "Low Inventory Alert";
        String message = "Item " + itemName + " is running low (only " + quantity + " left).";
        
        // Create an intent to open the owner dashboard
        Intent intent = new Intent(context, OwnerDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
                PendingIntent.FLAG_IMMUTABLE);
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_INVENTORY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        // Show the notification
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(NOTIFICATION_ID_LOW_INVENTORY, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for showing notification", e);
        }
    }
}
