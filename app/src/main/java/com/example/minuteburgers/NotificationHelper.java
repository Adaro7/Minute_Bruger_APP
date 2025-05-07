package com.example.minuteburgers;

import android.content.Context;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.auth.FirebaseAuth;

import java.util.HashMap;
import java.util.Map;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private final Context context;
    private final DatabaseReference notificationsRef;

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationsRef = FirebaseDatabase.getInstance().getReference("notifications");
    }

    // Send notification for order status updates
    public void sendOrderStatusNotification(String orderId, String status) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", "Order Status Update");
        notification.put("body", "Your order #" + orderId + " is " + status);
        notification.put("userId", userId);
        notification.put("type", "ORDER_STATUS");
        notification.put("timestamp", System.currentTimeMillis());

        // Store notification in Firebase
        notificationsRef.push().setValue(notification)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Notification saved successfully");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving notification", e);
            });
    }

    // Send notification for low inventory
    public void sendLowInventoryNotification(String itemName, int quantity) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", "Low Inventory Alert");
        notification.put("body", itemName + " is running low! Only " + quantity + " left");
        notification.put("type", "INVENTORY_ALERT");
        notification.put("timestamp", System.currentTimeMillis());

        notificationsRef.push().setValue(notification)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Low inventory notification saved");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving inventory notification", e);
            });
    }

    // Subscribe to specific topics
    public void subscribeToTopic(String topic) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Subscribed to topic: " + topic);
                } else {
                    Log.e(TAG, "Failed to subscribe to topic: " + topic);
                }
            });
    }

    // Unsubscribe from topics
    public void unsubscribeFromTopic(String topic) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Unsubscribed from topic: " + topic);
                } else {
                    Log.e(TAG, "Failed to unsubscribe from topic: " + topic);
                }
            });
    }
} 