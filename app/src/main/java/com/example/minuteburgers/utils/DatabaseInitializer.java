package com.example.minuteburgers.utils;

import android.util.Log;

import com.example.minuteburgers.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class to initialize database with required data like owner account
 */
public class DatabaseInitializer {
    private static final String TAG = "DatabaseInitializer";

    // Owner account details - in a real app, these would be stored securely
    private static final String OWNER_EMAIL = "owner@minuteburgers.com";
    private static final String OWNER_PASSWORD = "owner123456"; // This should be more secure in a real app
    private static final String OWNER_NAME = "Restaurant Owner";
    private static final String OWNER_BRANCH = "Main Branch";

    // Debug flag to force owner creation even if it exists
    // Set to true to ensure owner account is always created
    private static final boolean FORCE_OWNER_CREATION = true;

    /**
     * Ensures that an owner account exists in the database
     */
    public static void ensureOwnerExists(FirebaseAuth auth, FirebaseFirestore db) {
        Log.d(TAG, "Checking if owner account exists...");

        // If we're forcing owner creation, create it directly
        if (FORCE_OWNER_CREATION) {
            Log.d(TAG, "Forcing owner account creation");
            forceCreateOwnerAccount(auth, db);
            return;
        }

        // First check if owner account already exists
        db.collection("users")
            .whereEqualTo("role", "owner")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.isEmpty()) {
                    // No owner exists, create one
                    Log.d(TAG, "No owner account found, creating one");
                    createOwnerAccount(auth, db);
                } else {
                    Log.d(TAG, "Owner account already exists");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking for owner account", e);
                // On failure, try to create owner anyway
                createOwnerAccount(auth, db);
            });
    }

    /**
     * Force creates the owner account, deleting any existing account with the same email
     */
    public static void forceCreateOwnerAccount(FirebaseAuth auth, FirebaseFirestore db) {
        Log.d(TAG, "Force creating owner account: " + OWNER_EMAIL);

        // First check if the email is already registered
        auth.fetchSignInMethodsForEmail(OWNER_EMAIL)
            .addOnSuccessListener(signInMethodsResult -> {
                List<String> signInMethods = signInMethodsResult.getSignInMethods();

                if (signInMethods != null && !signInMethods.isEmpty()) {
                    // Email is already registered, try to sign in
                    Log.d(TAG, "Owner email already registered, attempting to sign in");

                    auth.signInWithEmailAndPassword(OWNER_EMAIL, OWNER_PASSWORD)
                        .addOnSuccessListener(authResult -> {
                            // Successfully signed in, now create/update the Firestore profile
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                Log.d(TAG, "Successfully signed in as owner: " + user.getUid());

                                // Create or update the owner profile in Firestore
                                createOwnerProfile(db, user.getUid());

                                // Sign out after creating/updating profile
                                auth.signOut();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sign in as owner: " + e.getMessage(), e);

                            // Send password reset email as a fallback
                            auth.sendPasswordResetEmail(OWNER_EMAIL)
                                .addOnSuccessListener(unused -> {
                                    Log.d(TAG, "Password reset email sent to owner");
                                })
                                .addOnFailureListener(e1 -> {
                                    Log.e(TAG, "Error sending password reset: " + e1.getMessage(), e1);
                                });
                        });
                } else {
                    // Email is not registered, create a new account
                    Log.d(TAG, "Owner email not registered, creating new account");

                    auth.createUserWithEmailAndPassword(OWNER_EMAIL, OWNER_PASSWORD)
                        .addOnSuccessListener(authResult -> {
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                Log.d(TAG, "Owner account created successfully: " + user.getUid());

                                // Create the owner profile in Firestore
                                createOwnerProfile(db, user.getUid());

                                // Sign out after creating profile
                                auth.signOut();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to create owner account: " + e.getMessage(), e);
                        });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking email registration: " + e.getMessage(), e);
            });
    }

    /**
     * Creates the owner account in Firebase Auth and Firestore
     */
    private static void createOwnerAccount(FirebaseAuth auth, FirebaseFirestore db) {
        Log.d(TAG, "Attempting to create/verify owner account: " + OWNER_EMAIL);

        // First check if the email is already registered
        auth.fetchSignInMethodsForEmail(OWNER_EMAIL)
            .addOnSuccessListener(signInMethodsResult -> {
                // Get the list of sign-in methods
                List<String> signInMethods = signInMethodsResult.getSignInMethods();

                // Check if there are no sign-in methods (email not registered)
                if (signInMethods == null || signInMethods.size() == 0) {
                    Log.d(TAG, "Owner email not registered, creating new account");
                    // Email not registered, create the account
                    auth.createUserWithEmailAndPassword(OWNER_EMAIL, OWNER_PASSWORD)
                        .addOnSuccessListener(authResult -> {
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                Log.d(TAG, "Owner auth account created successfully: " + user.getUid());
                                // Create user profile in Firestore
                                createOwnerProfile(db, user.getUid());

                                // Sign out after creating owner (so current user isn't changed)
                                auth.signOut();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error creating owner auth account: " + e.getMessage(), e);
                        });
                } else {
                    Log.d(TAG, "Owner email already registered, checking Firestore profile");

                    // Check if the user exists in Firestore first
                    db.collection("users")
                        .whereEqualTo("email", OWNER_EMAIL)
                        .whereEqualTo("role", "owner")
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            if (queryDocumentSnapshots.isEmpty()) {
                                Log.d(TAG, "Owner email exists in Auth but not in Firestore as owner");

                                // Try to sign in with the provided credentials
                                auth.signInWithEmailAndPassword(OWNER_EMAIL, OWNER_PASSWORD)
                                    .addOnSuccessListener(authResult -> {
                                        FirebaseUser user = authResult.getUser();
                                        if (user != null) {
                                            Log.d(TAG, "Successfully signed in as owner: " + user.getUid());
                                            // Create owner profile in Firestore
                                            createOwnerProfile(db, user.getUid());
                                            // Sign out after creating profile
                                            auth.signOut();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error signing in as owner: " + e.getMessage(), e);

                                        // If sign-in fails, send a password reset email
                                        auth.sendPasswordResetEmail(OWNER_EMAIL)
                                            .addOnSuccessListener(unused -> {
                                                Log.d(TAG, "Password reset email sent to owner");
                                            })
                                            .addOnFailureListener(e1 -> {
                                                Log.e(TAG, "Error sending password reset: " + e1.getMessage(), e1);
                                            });
                                    });
                            } else {
                                Log.d(TAG, "Owner account already exists in Firestore");
                                // Owner already exists in Firestore, no need to do anything
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error checking owner in Firestore: " + e.getMessage(), e);
                        });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking email registration: " + e.getMessage(), e);
            });
    }

    /**
     * Creates or updates the owner profile in Firestore
     */
    private static void createOwnerProfile(FirebaseFirestore db, String userId) {
        Log.d(TAG, "Creating/updating owner profile in Firestore for user: " + userId);

        // Create a complete owner data map
        Map<String, Object> ownerData = new HashMap<>();
        ownerData.put("uid", userId);
        ownerData.put("name", OWNER_NAME);
        ownerData.put("email", OWNER_EMAIL);
        ownerData.put("role", "owner");
        ownerData.put("branch", OWNER_BRANCH);
        ownerData.put("registrationDate", new Date());
        ownerData.put("lastUpdated", new Date());
        ownerData.put("isActive", true);
        ownerData.put("permissions", getOwnerPermissions());

        // First check if the document exists
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Document exists, update it
                    Log.d(TAG, "Owner profile exists, updating it");

                    db.collection("users")
                        .document(userId)
                        .update(ownerData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Owner profile updated successfully");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating owner profile: " + e.getMessage(), e);

                            // If update fails, try to set the document
                            db.collection("users")
                                .document(userId)
                                .set(ownerData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Owner profile created successfully after update failure");
                                })
                                .addOnFailureListener(e1 -> {
                                    Log.e(TAG, "Error creating owner profile after update failure: " + e1.getMessage(), e1);
                                });
                        });
                } else {
                    // Document doesn't exist, create it
                    Log.d(TAG, "Owner profile doesn't exist, creating it");

                    db.collection("users")
                        .document(userId)
                        .set(ownerData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Owner profile created successfully");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error creating owner profile: " + e.getMessage(), e);
                        });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking if owner profile exists: " + e.getMessage(), e);

                // If checking fails, try to set the document anyway
                db.collection("users")
                    .document(userId)
                    .set(ownerData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Owner profile created successfully after check failure");
                    })
                    .addOnFailureListener(e1 -> {
                        Log.e(TAG, "Error creating owner profile after check failure: " + e1.getMessage(), e1);
                    });
            });
    }

    /**
     * Returns a map of owner permissions
     */
    private static Map<String, Boolean> getOwnerPermissions() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("manageInventory", true);
        permissions.put("manageStaff", true);
        permissions.put("viewReports", true);
        permissions.put("approveRequests", true);
        permissions.put("accessOwnerDashboard", true);
        return permissions;
    }
}
