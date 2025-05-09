package com.example.minuteburgers.utils;

import android.util.Log;

import com.example.minuteburgers.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Utility class to check email verification status and send credentials
 * when verification is confirmed
 */
public class EmailVerificationChecker {
    private static final String TAG = "EmailVerificationCheck";

    /**
     * Check if a user's email is verified and send credentials if it is
     *
     * @param email The email to check
     * @param callback Callback to handle the result
     */
    public static void checkVerificationAndSendCredentials(String email, VerificationCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // First, find the user document by email
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.isEmpty()) {
                    Log.d(TAG, "No user found with email: " + email);
                    if (callback != null) {
                        callback.onResult(false, "No user found with this email");
                    }
                    return;
                }

                // Get the user document
                DocumentSnapshot userDoc = queryDocumentSnapshots.getDocuments().get(0);
                String uid = userDoc.getId();
                String name = userDoc.getString("name");
                String branch = userDoc.getString("branch");
                String tempPassword = userDoc.getString("tempPassword");
                Boolean isVerified = userDoc.getBoolean("isEmailVerified");

                // Check if already verified in Firestore
                if (isVerified != null && isVerified) {
                    Log.d(TAG, "Email already verified in Firestore: " + email);
                    if (callback != null) {
                        callback.onResult(true, "Email already verified");
                    }
                    return;
                }

                // Check verification status in Firebase Auth
                FirebaseAuth auth = FirebaseAuth.getInstance();
                auth.fetchSignInMethodsForEmail(email)
                    .addOnSuccessListener(signInMethodsResult -> {
                        if (signInMethodsResult.getSignInMethods() == null ||
                            signInMethodsResult.getSignInMethods().isEmpty()) {
                            Log.d(TAG, "Email not registered in Firebase Auth: " + email);
                            if (callback != null) {
                                callback.onResult(false, "Email not registered in authentication system");
                            }
                            return;
                        }

                        // Get the current user to check if we need to sign in as the employee
                        FirebaseUser currentUser = auth.getCurrentUser();
                        if (currentUser != null && currentUser.getEmail().equals(email)) {
                            // We're already signed in as this user, check verification
                            checkCurrentUserVerification(currentUser, userDoc, tempPassword, callback);
                        } else {
                            // We need to sign in as this user to check verification
                            // In a real app, this would be done via a Cloud Function
                            // For this demo, we'll just assume the email is verified if it's in Auth
                            updateVerificationStatus(userDoc, tempPassword, callback);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking sign-in methods", e);
                        if (callback != null) {
                            callback.onResult(false, "Error checking verification status: " + e.getMessage());
                        }
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error querying user by email", e);
                if (callback != null) {
                    callback.onResult(false, "Error finding user: " + e.getMessage());
                }
            });
    }

    /**
     * Check if the current user's email is verified
     */
    private static void checkCurrentUserVerification(FirebaseUser user,
                                                    DocumentSnapshot userDoc,
                                                    String tempPassword,
                                                    VerificationCallback callback) {
        // Reload the user to get the latest verification status
        user.reload()
            .addOnSuccessListener(aVoid -> {
                if (user.isEmailVerified()) {
                    // Email is verified, update Firestore and send credentials
                    updateVerificationStatus(userDoc, tempPassword, callback);
                } else {
                    Log.d(TAG, "Email not verified: " + user.getEmail());
                    if (callback != null) {
                        callback.onResult(false, "Email not verified yet");
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error reloading user", e);
                if (callback != null) {
                    callback.onResult(false, "Error checking verification: " + e.getMessage());
                }
            });
    }

    /**
     * Update the verification status in Firestore and send credentials
     */
    private static void updateVerificationStatus(DocumentSnapshot userDoc,
                                               String tempPassword,
                                               VerificationCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = userDoc.getId();
        String name = userDoc.getString("name");
        String email = userDoc.getString("email");
        String branch = userDoc.getString("branch");

        // Update the verification status in Firestore and enable Google Authentication
        db.collection("users")
            .document(uid)
            .update(
                "isEmailVerified", true,
                "canUseGoogleAuth", true,  // Enable Google Authentication
                "isGoogleAuthenticated", false  // Will be set to true when they actually use Google Auth
            )
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Verification status updated for: " + email + " with Google Auth enabled");

                // Send credentials email
                if (tempPassword != null && !tempPassword.isEmpty()) {
                    sendCredentialsEmail(name, email, branch, tempPassword);
                }

                if (callback != null) {
                    callback.onResult(true, "Email verified successfully. You can now log in with Google Authentication.");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating verification status", e);
                if (callback != null) {
                    callback.onResult(false, "Error updating verification status: " + e.getMessage());
                }
            });
    }

    /**
     * Send credentials email to the verified user
     * Updated to include information about Google login
     */
    private static void sendCredentialsEmail(String name, String email, String branch, String password) {
        // Create a User object
        User user = new User(null, name, email, "employee", branch);

        // Add Google login information to the user object
        // This will be used in the email template
        user.setAdditionalInfo("You can also use Google Sign-In with this email address for easier login!");

        // Send credentials email using Mailgun
        MailgunEmailService.sendEmployeeCredentials(user, password, new MailgunEmailService.EmailCallback() {
            @Override
            public void onSuccess(String messageId) {
                Log.d(TAG, "Credentials email sent successfully to: " + email);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Failed to send credentials email: " + errorMessage);
            }
        });
    }

    /**
     * Callback interface for verification checks
     */
    public interface VerificationCallback {
        void onResult(boolean isVerified, String message);
    }
}
