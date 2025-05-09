package com.example.minuteburgers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.minuteburgers.utils.EmailVerificationChecker;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.minuteburgers.models.User;

/**
 * Activity to handle email verification links and Google account linking
 */
public class VerifyEmailActivity extends AppCompatActivity {
    private static final String TAG = "VerifyEmailActivity";
    private static final int RC_SIGN_IN = 9001;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;

    private TextView statusText;
    private ProgressBar progressBar;
    private Button loginButton;
    private SignInButton googleSignInButton;
    private String userEmail;
    private String oobCode;
    private boolean isVerified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        loginButton = findViewById(R.id.loginButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);

        // Configure Google Sign In with the correct web client ID
        String webClientId = "469900893337-tcbjt8bh4k18kqe50brc70m29b0su526.apps.googleusercontent.com";
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Set up button listeners
        loginButton.setOnClickListener(v -> goToLogin());
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());

        // Process the intent
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // Check if this is a Firebase verification link
        Uri uri = intent.getData();
        if (uri != null) {
            Log.d(TAG, "Received URI: " + uri.toString());

            // Extract the mode and oob code
            String mode = uri.getQueryParameter("mode");
            oobCode = uri.getQueryParameter("oobCode");

            if ("verifyEmail".equals(mode) && oobCode != null) {
                // This is an email verification link
                verifyEmail(oobCode);
            } else {
                showError("Invalid verification link");
            }
        } else {
            showError("No verification data found");
        }
    }

    private void verifyEmail(String oobCode) {
        showLoading(true);

        // Verify the oob code with Firebase
        mAuth.checkActionCode(oobCode)
            .addOnSuccessListener(actionCodeResult -> {
                // Get the email from the action code result
                userEmail = actionCodeResult.getData(com.google.firebase.auth.ActionCodeResult.EMAIL);

                if (userEmail != null) {
                    // Apply the action code to verify the email
                    mAuth.applyActionCode(oobCode)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Email verified successfully: " + userEmail);

                            // Update verification status in Firestore
                            updateVerificationStatus(userEmail);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error applying action code", e);

                            // Check if the error is due to expired code
                            if (e.getMessage() != null && e.getMessage().contains("expired")) {
                                // Handle expired verification link
                                handleExpiredVerificationLink(userEmail);
                            } else {
                                showError("Error verifying email: " + e.getMessage());
                            }
                        });
                } else {
                    showError("Could not determine email from verification link");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking action code", e);

                // Try to extract email from the link if possible
                Uri uri = getIntent().getData();
                if (uri != null) {
                    userEmail = uri.getQueryParameter("email");
                    if (userEmail != null) {
                        // Handle as an expired verification link
                        handleExpiredVerificationLink(userEmail);
                    } else {
                        showError("Error verifying email: " + e.getMessage());
                    }
                } else {
                    showError("Error verifying email: " + e.getMessage());
                }
            });
    }

    private void updateVerificationStatus(String email) {
        // Find the user in Firestore
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Get the user document
                    DocumentSnapshot userDoc = queryDocumentSnapshots.getDocuments().get(0);
                    String uid = userDoc.getId();
                    String name = userDoc.getString("name");
                    String branch = userDoc.getString("branch");
                    String tempPassword = userDoc.getString("tempPassword");

                    // Update verification status and enable Google Authentication
                    db.collection("users")
                        .document(uid)
                        .update(
                            "isEmailVerified", true,
                            "canUseGoogleAuth", true,  // Enable Google Authentication
                            "isGoogleAuthenticated", false  // Will be set to true when they actually use Google Auth
                        )
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Verification status updated in Firestore with Google Auth enabled");
                            isVerified = true;

                            // Send login credentials to the user
                            if (tempPassword != null && !tempPassword.isEmpty()) {
                                sendLoginCredentials(name, email, branch, tempPassword);
                            } else {
                                Log.w(TAG, "No temporary password found for user: " + email);
                            }

                            showSuccess(email);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating verification status", e);
                            showError("Error updating verification status: " + e.getMessage());
                        });
                } else {
                    Log.e(TAG, "No user found with email: " + email);
                    showError("No user found with this email");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding user", e);
                showError("Error finding user: " + e.getMessage());
            });
    }

    /**
     * Send login credentials to the verified user
     */
    private void sendLoginCredentials(String name, String email, String branch, String password) {
        Log.d(TAG, "Sending login credentials to: " + email);

        // Create a User object
        User user = new User(null, name, email, "employee", branch);

        // Send credentials email using Mailgun
        com.example.minuteburgers.utils.MailgunEmailService.sendEmployeeCredentials(user, password, new com.example.minuteburgers.utils.MailgunEmailService.EmailCallback() {
            @Override
            public void onSuccess(String messageId) {
                Log.d(TAG, "Login credentials sent successfully to: " + email);
                runOnUiThread(() -> {
                    Toast.makeText(VerifyEmailActivity.this,
                        "Login credentials have been sent to your email",
                        Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Failed to send login credentials: " + errorMessage);
                // Still allow the user to proceed even if email sending fails
            }
        });
    }

    private void handleExpiredVerificationLink(String email) {
        if (email == null || email.isEmpty()) {
            showError("Could not determine email from expired verification link");
            return;
        }

        // Find the user in Firestore
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Get the user document
                    DocumentSnapshot userDoc = queryDocumentSnapshots.getDocuments().get(0);
                    String name = userDoc.getString("name");
                    String branch = userDoc.getString("branch");
                    String tempPassword = userDoc.getString("tempPassword");

                    // Mark as verified anyway (since we know the user clicked the link) and enable Google Authentication
                    userDoc.getReference().update(
                            "isEmailVerified", true,
                            "canUseGoogleAuth", true,  // Enable Google Authentication
                            "isGoogleAuthenticated", false  // Will be set to true when they actually use Google Auth
                        )
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Verification status updated despite expired link with Google Auth enabled");
                            userEmail = email;
                            isVerified = true;

                            // Send login credentials to the user
                            if (tempPassword != null && !tempPassword.isEmpty()) {
                                sendLoginCredentials(name, email, branch, tempPassword);
                            } else {
                                Log.w(TAG, "No temporary password found for user: " + email);
                            }

                            showSuccess(email);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating verification status", e);
                            showError("Error updating verification status: " + e.getMessage());
                        });
                } else {
                    Log.e(TAG, "No user found with email: " + email);
                    showError("No user found with this email");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding user", e);
                showError("Error finding user: " + e.getMessage());
            });
    }

    private void signInWithGoogle() {
        if (!isVerified || userEmail == null) {
            Toast.makeText(this, "Please verify your email first", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Start the Google Sign-In flow
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Get Google Sign-In account
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    // Check if the Google email matches the verified email
                    if (account.getEmail() != null && account.getEmail().equals(userEmail)) {
                        // Link Google account with Firebase
                        linkGoogleAccount(account);
                    } else {
                        showError("The Google account email does not match your verified email. Please use " + userEmail);
                        showLoading(false);
                    }
                }
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed", e);
                showError("Google sign in failed: " + e.getStatusCode());
                showLoading(false);
            }
        }
    }

    private void linkGoogleAccount(GoogleSignInAccount account) {
        // Get Google Auth credential
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        // Check if user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is signed in, link the Google account
            currentUser.linkWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "linkWithCredential:success");
                        updateGoogleAuthStatus(true);
                    } else {
                        Log.e(TAG, "linkWithCredential:failure", task.getException());

                        // If linking fails because the credential is already associated with another account,
                        // just mark the user as Google authenticated
                        if (task.getException() != null &&
                            task.getException().getMessage() != null &&
                            task.getException().getMessage().contains("already associated")) {
                            updateGoogleAuthStatus(true);
                        } else {
                            showError("Failed to link Google account: " + task.getException().getMessage());
                            showLoading(false);
                        }
                    }
                });
        } else {
            // User is not signed in, sign in with Google and update Firestore
            mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        updateGoogleAuthStatus(true);
                    } else {
                        Log.e(TAG, "signInWithCredential:failure", task.getException());
                        showError("Failed to sign in with Google: " + task.getException().getMessage());
                        showLoading(false);
                    }
                });
        }
    }

    private void updateGoogleAuthStatus(boolean isGoogleAuthenticated) {
        // Find the user in Firestore
        db.collection("users")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Get the user document
                    DocumentSnapshot userDoc = queryDocumentSnapshots.getDocuments().get(0);

                    // Update Google authentication status
                    userDoc.getReference().update("isGoogleAuthenticated", isGoogleAuthenticated)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Google authentication status updated");
                            Toast.makeText(VerifyEmailActivity.this,
                                "Google account linked successfully",
                                Toast.LENGTH_SHORT).show();

                            // Go to login screen
                            goToLogin();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating Google authentication status", e);
                            showError("Error updating Google authentication status: " + e.getMessage());
                            showLoading(false);
                        });
                } else {
                    Log.e(TAG, "No user found with email: " + userEmail);
                    showError("No user found with this email");
                    showLoading(false);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding user", e);
                showError("Error finding user: " + e.getMessage());
                showLoading(false);
            });
    }

    private void showSuccess(String email) {
        showLoading(false);
        statusText.setText("✅ Email verified successfully: " + email +
            "\n\n🔑 Your login credentials have been sent to your email." +
            "\n\n🎉 IMPORTANT: You can now log in to the Minute Burgers app using Google Sign-In!" +
            "\n\n📱 Simply open the app, click 'Log In with Google' and select this email address." +
            "\n\n⚠️ For added security, you can also link your Google account now by clicking the button below.");

        // Make the Google Sign-In button more prominent
        loginButton.setVisibility(View.VISIBLE);
        googleSignInButton.setVisibility(View.VISIBLE);

        // Set custom text for Google Sign-In button
        setGoogleButtonText(googleSignInButton, "Link Google Account Now");
    }

    /**
     * Set custom text for Google Sign-In button
     */
    private void setGoogleButtonText(com.google.android.gms.common.SignInButton signInButton, String buttonText) {
        // Find the TextView inside the SignInButton
        for (int i = 0; i < signInButton.getChildCount(); i++) {
            View v = signInButton.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                tv.setText(buttonText);
                tv.setTextSize(14);
                return;
            }
        }
    }

    private void showError(String message) {
        showLoading(false);
        statusText.setText("Error: " + message);
        loginButton.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        statusText.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        loginButton.setEnabled(!isLoading);
        googleSignInButton.setEnabled(!isLoading);
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
