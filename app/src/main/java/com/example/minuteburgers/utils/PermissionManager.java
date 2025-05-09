package com.example.minuteburgers.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class to manage user permissions based on roles
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    
    // Singleton instance
    private static PermissionManager instance;
    
    // User permissions cache
    private Map<String, Boolean> userPermissions = new HashMap<>();
    private String userRole = "";
    private boolean isPermissionsLoaded = false;
    
    // Permission constants
    public static final String MANAGE_INVENTORY = "manageInventory";
    public static final String MANAGE_STAFF = "manageStaff";
    public static final String VIEW_REPORTS = "viewReports";
    public static final String APPROVE_REQUESTS = "approveRequests";
    public static final String ACCESS_OWNER_DASHBOARD = "accessOwnerDashboard";
    public static final String REQUEST_STOCK = "requestStock";
    
    // Private constructor for singleton
    private PermissionManager() {
        // Initialize default permissions
        resetPermissions();
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized PermissionManager getInstance() {
        if (instance == null) {
            instance = new PermissionManager();
        }
        return instance;
    }
    
    /**
     * Reset permissions to default (no permissions)
     */
    public void resetPermissions() {
        userPermissions.clear();
        userRole = "";
        isPermissionsLoaded = false;
        
        // Set default permissions (all false)
        userPermissions.put(MANAGE_INVENTORY, false);
        userPermissions.put(MANAGE_STAFF, false);
        userPermissions.put(VIEW_REPORTS, false);
        userPermissions.put(APPROVE_REQUESTS, false);
        userPermissions.put(ACCESS_OWNER_DASHBOARD, false);
        userPermissions.put(REQUEST_STOCK, false);
    }
    
    /**
     * Load permissions for the current user
     */
    public void loadUserPermissions(FirebaseAuth auth, FirebaseFirestore db, PermissionCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No user logged in");
            resetPermissions();
            if (callback != null) {
                callback.onPermissionsLoaded(false);
            }
            return;
        }
        
        String userId = user.getUid();
        Log.d(TAG, "Loading permissions for user: " + userId);
        
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Get user role
                    userRole = documentSnapshot.getString("role");
                    Log.d(TAG, "User role: " + userRole);
                    
                    // Check if permissions are directly stored in the user document
                    Map<String, Boolean> permissions = (Map<String, Boolean>) documentSnapshot.get("permissions");
                    
                    if (permissions != null) {
                        // Use stored permissions
                        userPermissions = permissions;
                        Log.d(TAG, "Loaded custom permissions: " + userPermissions.toString());
                    } else {
                        // Set permissions based on role
                        setPermissionsByRole(userRole);
                        Log.d(TAG, "Set permissions by role: " + userRole);
                    }
                    
                    isPermissionsLoaded = true;
                    
                    if (callback != null) {
                        callback.onPermissionsLoaded(true);
                    }
                } else {
                    Log.d(TAG, "User document does not exist");
                    resetPermissions();
                    if (callback != null) {
                        callback.onPermissionsLoaded(false);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user permissions: " + e.getMessage(), e);
                resetPermissions();
                if (callback != null) {
                    callback.onPermissionsLoaded(false);
                }
            });
    }
    
    /**
     * Set permissions based on user role
     */
    private void setPermissionsByRole(String role) {
        resetPermissions();
        
        if ("owner".equalsIgnoreCase(role)) {
            // Owner has all permissions
            userPermissions.put(MANAGE_INVENTORY, true);
            userPermissions.put(MANAGE_STAFF, true);
            userPermissions.put(VIEW_REPORTS, true);
            userPermissions.put(APPROVE_REQUESTS, true);
            userPermissions.put(ACCESS_OWNER_DASHBOARD, true);
            userPermissions.put(REQUEST_STOCK, false); // Owners don't request stock, they approve requests
        } else if ("employee".equalsIgnoreCase(role)) {
            // Employees have limited permissions
            userPermissions.put(MANAGE_INVENTORY, true); // Can manage inventory
            userPermissions.put(MANAGE_STAFF, false); // Cannot manage staff
            userPermissions.put(VIEW_REPORTS, true); // Can view reports
            userPermissions.put(APPROVE_REQUESTS, false); // Cannot approve requests
            userPermissions.put(ACCESS_OWNER_DASHBOARD, false); // Cannot access owner dashboard
            userPermissions.put(REQUEST_STOCK, true); // Can request stock
        }
    }
    
    /**
     * Check if the user has a specific permission
     */
    public boolean hasPermission(String permission) {
        if (!isPermissionsLoaded) {
            Log.w(TAG, "Permissions not loaded yet");
            return false;
        }
        
        Boolean hasPermission = userPermissions.get(permission);
        return hasPermission != null && hasPermission;
    }
    
    /**
     * Get the user's role
     */
    public String getUserRole() {
        return userRole;
    }
    
    /**
     * Check if the user is an owner
     */
    public boolean isOwner() {
        return "owner".equalsIgnoreCase(userRole);
    }
    
    /**
     * Check if the user is an employee
     */
    public boolean isEmployee() {
        return "employee".equalsIgnoreCase(userRole);
    }
    
    /**
     * Callback interface for permission loading
     */
    public interface PermissionCallback {
        void onPermissionsLoaded(boolean success);
    }
}
