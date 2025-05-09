package com.example.minuteburgers;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.minuteburgers.utils.EmailVerificationChecker;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private TextInputEditText emailEditText, passwordEditText;
    private MaterialButton loginButton;
    private SignInButton googleSignInButton;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if Google Services JSON is properly configured
        checkGoogleServicesConfig();

        // Hide the owner hint text
        TextView ownerHintText = findViewById(R.id.ownerHintText);
        ownerHintText.setVisibility(View.GONE);

        // Configure Google Sign In using the default web client ID from resources
        initializeGoogleSignIn();

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        View signUpTextView = findViewById(R.id.signUpTextView);

        // Customize Google Sign-In button text
        googleSignInButton.setSize(SignInButton.SIZE_STANDARD);
        ((TextView) googleSignInButton.getChildAt(0)).setText("Log In with Google");

        loginButton.setOnClickListener(v -> handleEmailLogin());
        googleSignInButton.setOnClickListener(v -> handleGoogleSignIn());

        // Hide signup option - employees are now added by the owner
        signUpTextView.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check if user is already signed in with Firebase
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already signed in, check their role and redirect
            Log.d(TAG, "User already signed in with Firebase: " + currentUser.getEmail());
            checkUserRoleAndRedirect(currentUser);
            return;
        }

        // Log the state of Google Sign-In, but don't automatically sign in
        // This is to prevent unexpected behavior and let the user explicitly choose to sign in
        GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (lastAccount != null) {
            Log.d(TAG, "Found existing Google Sign-In account: " + lastAccount.getEmail() +
                  ", but waiting for user to explicitly sign in");

            // We don't automatically sign in here, just log the state
            // The user will need to click the Google Sign-In button
        }
    }

    private void handleEmailLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if this is the owner login
        boolean isOwnerLogin = email.equals("owner@minuteburgers.com");
        if (isOwnerLogin) {
            Log.d(TAG, "Owner login detected");
            // Owner login doesn't need verification, proceed directly
            proceedWithEmailLogin(email, password, isOwnerLogin);
            return;
        }

        // For employee logins, check verification status first
        setLoadingState(true);

        // Check if this is an employee account that needs verification
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    // User exists in Firestore
                    DocumentSnapshot userDoc = queryDocumentSnapshots.getDocuments().get(0);
                    String userRole = userDoc.getString("role");
                    Boolean isEmailVerified = userDoc.getBoolean("isEmailVerified");

                    // If this is an employee, check if their email is verified
                    if ("employee".equals(userRole) && (isEmailVerified == null || !isEmailVerified)) {
                        // Check verification status before allowing login
                        EmailVerificationChecker.checkVerificationAndSendCredentials(email, new EmailVerificationChecker.VerificationCallback() {
                            @Override
                            public void onResult(boolean isVerified, String message) {
                                if (isVerified) {
                                    // Email is verified, proceed with login
                                    proceedWithEmailLogin(email, password, false);
                                } else {
                                    // Email is not verified, show message
                                    setLoadingState(false);
                                    Log.d(TAG, "Email not verified: " + message);

                                    // Show verification required dialog
                                    new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                                        .setTitle("Email Verification Required")
                                        .setMessage("Your email address (" + email + ") has not been verified yet.\n\n" +
                                                "Please check your email for a verification link and verify your email before logging in.\n\n" +
                                                "Once verified, you will receive your login credentials.")
                                        .setPositiveButton("OK", null)
                                        .show();
                                }
                            }
                        });
                    } else {
                        // Owner or already verified employee, proceed with login
                        proceedWithEmailLogin(email, password, false);
                    }
                } else {
                    // User doesn't exist in Firestore, try login anyway
                    // This handles cases where the user might exist in Auth but not in Firestore
                    proceedWithEmailLogin(email, password, false);
                }
            })
            .addOnFailureListener(e -> {
                // Error checking Firestore, proceed with login anyway
                Log.e(TAG, "Error checking user in Firestore", e);
                proceedWithEmailLogin(email, password, false);
            });
    }

    /**
     * Proceed with email login after verification check
     */
    private void proceedWithEmailLogin(String email, String password, boolean isOwnerLogin) {
        // Show loading state if not already shown
        setLoadingState(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoadingState(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // If this is the owner login, verify the user has the owner role in Firestore
                            // Check user role and redirect accordingly
                            checkUserRoleAndRedirect(user);
                        }
                    } else {
                        String errorMessage = "Authentication failed";
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();

                            // Log detailed error for debugging
                            Log.e(TAG, "Authentication error", task.getException());
                        }

                        // Special handling for owner login failures
                        if (isOwnerLogin) {
                            errorMessage = "Owner authentication failed. Please check your password or contact support.";
                        }

                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Handle Google Sign-In button click
     * This method implements a more robust approach to Google Sign-In
     */
    private void handleGoogleSignIn() {
        try {
            Log.d(TAG, "Starting Google Sign In process");
            setLoadingState(true);

            // Check if Google Sign-In client is initialized
            if (mGoogleSignInClient == null) {
                Log.e(TAG, "Google Sign-In client is null, attempting to reinitialize");
                initializeGoogleSignIn();

                // Check again after initialization
                if (mGoogleSignInClient == null) {
                    Log.e(TAG, "Failed to initialize Google Sign-In client");
                    setLoadingState(false);
                    Toast.makeText(this, "Google Sign-In is not available. Please try email login.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // First, check if we already have a signed-in Google account
            GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
            if (lastAccount != null && !lastAccount.isExpired()) {
                Log.d(TAG, "Found existing Google Sign-In account: " + lastAccount.getEmail());

                // Verify if this account exists in our system
                checkIfUserExists(lastAccount);
                return;
            }

            // No existing account or it's expired, try silent sign-in
            Log.d(TAG, "No valid existing account, trying silent sign-in");

            // Create a new task for silent sign-in
            Task<GoogleSignInAccount> silentSignInTask = mGoogleSignInClient.silentSignIn();

            // Check if the task completed synchronously
            if (silentSignInTask.isComplete()) {
                if (silentSignInTask.isSuccessful()) {
                    // Silent sign-in succeeded synchronously
                    GoogleSignInAccount account = silentSignInTask.getResult();
                    Log.d(TAG, "Silent sign-in completed synchronously and successfully");
                    if (account != null) {
                        Log.d(TAG, "Silent Google Sign-In successful: " + account.getEmail());
                        checkIfUserExists(account);
                    } else {
                        Log.d(TAG, "Silent sign-in returned null account, showing sign-in UI");
                        showGoogleSignInUI();
                    }
                } else {
                    // Silent sign-in failed synchronously
                    Exception exception = silentSignInTask.getException();
                    Log.d(TAG, "Silent sign-in failed synchronously: " +
                          (exception != null ? exception.getMessage() : "Unknown error"));
                    showGoogleSignInUI();
                }
            } else {
                // Task is not complete yet, add a listener
                Log.d(TAG, "Silent sign-in task is not complete, adding listener");

                silentSignInTask.addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Silent sign-in succeeded asynchronously
                        GoogleSignInAccount account = task.getResult();
                        Log.d(TAG, "Silent sign-in completed asynchronously and successfully");
                        if (account != null) {
                            Log.d(TAG, "Silent Google Sign-In successful: " + account.getEmail());
                            checkIfUserExists(account);
                        } else {
                            Log.d(TAG, "Silent sign-in returned null account, showing sign-in UI");
                            showGoogleSignInUI();
                        }
                    } else {
                        // Silent sign-in failed asynchronously
                        Exception exception = task.getException();
                        Log.d(TAG, "Silent sign-in failed asynchronously: " +
                              (exception != null ? exception.getMessage() : "Unknown error"));
                        showGoogleSignInUI();
                    }
                });
            }
        } catch (Exception e) {
            setLoadingState(false);
            Log.e(TAG, "Error starting Google Sign In: ", e);

            // Try to reinitialize Google Sign-In
            try {
                initializeGoogleSignIn();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to reinitialize Google Sign-In", ex);
            }

            // Show a more detailed error message
            final String errorMessage = "Error starting Google Sign In: " + e.getMessage();
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();

            // Show an alert dialog with more details and troubleshooting steps
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Google Sign In Error")
                .setMessage("There was a problem starting Google Sign In. This might be due to:\n\n" +
                        "1. Missing or incorrect SHA-1 certificate\n" +
                        "2. Outdated Google Play Services\n" +
                        "3. Network connectivity issues\n\n" +
                        "Error details: " + e.getMessage())
                .setPositiveButton("Try Again", (dialog, which) -> {
                    // Try to reinitialize and sign in again
                    initializeGoogleSignIn();
                    if (mGoogleSignInClient != null) {
                        showGoogleSignInUI();
                    }
                })
                .setNegativeButton("Use Email Login", null)
                .show();
        }
    }

    /**
     * Show the Google Sign-In UI for user selection
     * This method handles the actual launching of the Google Sign-In UI
     */
    private void showGoogleSignInUI() {
        try {
            // Check if Google Sign-In client is initialized
            if (mGoogleSignInClient == null) {
                Log.e(TAG, "Google Sign-In client is null, attempting to reinitialize");
                initializeGoogleSignIn();

                // Check again after initialization
                if (mGoogleSignInClient == null) {
                    Log.e(TAG, "Failed to initialize Google Sign-In client");
                    setLoadingState(false);
                    Toast.makeText(this, "Google Sign-In is not available. Please try email login.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Show a toast to indicate the process is starting
            Toast.makeText(this, "Opening Google Sign In...", Toast.LENGTH_SHORT).show();

            // Create a new sign-in intent directly without signing out first
            // This approach is more reliable on some devices
            try {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                Log.d(TAG, "Google Sign In intent created: " + signInIntent);

                // Start the sign-in activity
                startActivityForResult(signInIntent, RC_SIGN_IN);
            } catch (Exception e) {
                Log.e(TAG, "Error creating sign-in intent", e);
                setLoadingState(false);
                Toast.makeText(LoginActivity.this, "Error launching Google Sign In: " + e.getMessage(), Toast.LENGTH_LONG).show();

                // Try alternative approach with sign out first
                tryAlternativeGoogleSignIn();
            }
        } catch (Exception e) {
            setLoadingState(false);
            Log.e(TAG, "Error showing Google Sign In UI: ", e);

            // Show error message
            Toast.makeText(this, "Error launching Google Sign In: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // Try to reinitialize Google Sign-In
            try {
                initializeGoogleSignIn();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to reinitialize Google Sign-In", ex);
            }
        }
    }

    /**
     * Try an alternative approach to Google Sign-In by signing out first
     * This can help on some devices where the account picker doesn't show up
     */
    private void tryAlternativeGoogleSignIn() {
        try {
            Log.d(TAG, "Trying alternative Google Sign-In approach (sign out first)");

            // Sign out first to ensure the account picker is shown
            mGoogleSignInClient.signOut().addOnCompleteListener(signOutTask -> {
                try {
                    // Create a new sign-in intent after sign out
                    Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                    Log.d(TAG, "Alternative Google Sign In intent created after sign out");

                    // Start the sign-in activity
                    startActivityForResult(signInIntent, RC_SIGN_IN);
                } catch (Exception e) {
                    Log.e(TAG, "Error in alternative sign-in approach", e);
                    setLoadingState(false);
                    Toast.makeText(LoginActivity.this,
                        "Google Sign In is not working. Please try email login instead.",
                        Toast.LENGTH_LONG).show();
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error signing out in alternative approach", e);
                setLoadingState(false);
                Toast.makeText(this,
                    "Google Sign In is not working. Please try email login instead.",
                    Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in alternative Google Sign-In approach", e);
            setLoadingState(false);
            Toast.makeText(this,
                "Google Sign In is not working. Please try email login instead.",
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "Google Sign In result received. Result code: " + resultCode);

            // Always try to get the account from the intent data first
            if (data != null) {
                try {
                    // Process the sign-in result directly
                    Log.d(TAG, "Processing Google Sign-In result from intent data");
                    processGoogleSignInResult(data);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error processing Google Sign-In result from intent data", e);
                    // Continue to fallback methods
                }
            }

            // If we couldn't get the account from intent data, try to get the last signed-in account
            GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
            if (lastAccount != null && !lastAccount.isExpired()) {
                Log.d(TAG, "Found valid last signed-in account: " + lastAccount.getEmail());

                // Verify if this account exists in our system
                checkIfUserExists(lastAccount);
                return;
            }

            // If we still don't have an account, try the alternative approach
            Log.d(TAG, "No valid account found, trying alternative sign-in approach");
            tryAlternativeGoogleSignIn();
        }
    }

    /**
     * Process the Google Sign In result
     * Updated to be more robust and handle various edge cases
     */
    private void processGoogleSignInResult(Intent data) {
        try {
            Log.d(TAG, "Processing Google Sign-In result");

            // First try to get the account directly from the intent
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            // Try to get the result immediately (this might throw an exception)
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    Log.d(TAG, "Successfully retrieved Google account synchronously: " + account.getEmail());
                    checkIfUserExists(account);
                    return;
                }
            } catch (ApiException e) {
                // Handle specific API exception status codes
                int statusCode = e.getStatusCode();

                // Log the status code for debugging
                Log.e(TAG, "Google Sign In error. Status code: " + statusCode, e);

                if (statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    // Status code 12501: Sign-in was cancelled
                    // This is often a false negative on some devices
                    Log.d(TAG, "Sign-in was cancelled according to API, but checking for last account anyway");

                    // Try to get the last signed-in account as a fallback
                    GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
                    if (lastAccount != null && !lastAccount.isExpired()) {
                        Log.d(TAG, "Found valid last signed-in account despite cancellation: " + lastAccount.getEmail());
                        checkIfUserExists(lastAccount);
                        return;
                    }
                } else if (statusCode == GoogleSignInStatusCodes.DEVELOPER_ERROR) {
                    // Status code 10: Developer error
                    // This typically means there's a mismatch between your app's configuration and the Google Cloud project settings
                    Log.e(TAG, "Google Sign In configuration error. Please check Firebase setup. Status code: " + statusCode);

                    // Show a more detailed error message
                    setLoadingState(false);
                    showDeveloperErrorDialog();
                    return;
                } else if (statusCode == GoogleSignInStatusCodes.NETWORK_ERROR) {
                    // Status code 7: Network error
                    Log.e(TAG, "Google Sign In network error. Status code: " + statusCode);
                    Toast.makeText(this, "Network error during Google Sign In. Please check your internet connection.", Toast.LENGTH_LONG).show();
                } else {
                    // For other API exceptions, log and continue to the async approach
                    Log.e(TAG, "ApiException in synchronous getResult: " + statusCode, e);
                }
            } catch (Exception e) {
                // For other exceptions, log and continue to the async approach
                Log.e(TAG, "Exception in synchronous getResult", e);
            }

            // If we couldn't get the result synchronously, try the asynchronous approach
            Log.d(TAG, "Trying asynchronous approach for Google Sign-In");

            // Add a completion listener to handle the result asynchronously
            task.addOnCompleteListener(signInTask -> {
                try {
                    if (signInTask.isSuccessful()) {
                        GoogleSignInAccount account = signInTask.getResult();

                        if (account == null) {
                            Log.e(TAG, "Google Sign In failed. Account is null");

                            // Try one more time with the last signed-in account
                            GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
                            if (lastAccount != null && !lastAccount.isExpired()) {
                                Log.d(TAG, "Found valid last signed-in account as final fallback: " + lastAccount.getEmail());
                                checkIfUserExists(lastAccount);
                                return;
                            }

                            Toast.makeText(this, "Google Sign In failed. No account data received", Toast.LENGTH_SHORT).show();
                            setLoadingState(false);
                            return;
                        }

                        Log.d(TAG, "Google Sign In successful. Email: " + account.getEmail());

                        // Check if this Google account is registered
                        checkIfUserExists(account);
                    } else {
                        // Handle the error
                        Exception exception = signInTask.getException();

                        // Try one more time with the last signed-in account before giving up
                        GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
                        if (lastAccount != null && !lastAccount.isExpired()) {
                            Log.d(TAG, "Found valid last signed-in account despite task failure: " + lastAccount.getEmail());
                            checkIfUserExists(lastAccount);
                            return;
                        }

                        handleGoogleSignInError(exception);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing Google Sign In result", e);

                    // Try one more time with the last signed-in account
                    GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
                    if (lastAccount != null && !lastAccount.isExpired()) {
                        Log.d(TAG, "Found valid last signed-in account despite exception: " + lastAccount.getEmail());
                        checkIfUserExists(lastAccount);
                        return;
                    }

                    Toast.makeText(this, "Error processing Google Sign In: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setLoadingState(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during Google Sign In", e);

            // Try one more time with the last signed-in account
            GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
            if (lastAccount != null && !lastAccount.isExpired()) {
                Log.d(TAG, "Found valid last signed-in account despite unexpected error: " + lastAccount.getEmail());
                checkIfUserExists(lastAccount);
                return;
            }

            Toast.makeText(this, "Unexpected error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            setLoadingState(false);
        }
    }

    /**
     * Handle Google Sign In errors
     */
    private void handleGoogleSignInError(Exception exception) {
        String errorMessage = "Google Sign In failed";

        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            int statusCode = apiException.getStatusCode();

            // Provide more specific error messages for common error codes
            switch (statusCode) {
                case GoogleSignInStatusCodes.SIGN_IN_CANCELLED:
                    errorMessage = "Google Sign In was cancelled";
                    break;
                case GoogleSignInStatusCodes.SIGN_IN_FAILED:
                    errorMessage = "Google Sign In failed. Please try again";
                    break;
                case GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS:
                    errorMessage = "Google Sign In is already in progress";
                    break;
                case GoogleSignInStatusCodes.INVALID_ACCOUNT:
                    errorMessage = "Invalid Google account selected";
                    break;
                case GoogleSignInStatusCodes.NETWORK_ERROR:
                    errorMessage = "Network error. Please check your connection";
                    break;
                case 10: // Common error code for misconfiguration
                    errorMessage = "Google Sign In configuration error. Please check Firebase setup";
                    break;
                default:
                    errorMessage = "Google Sign In failed (code: " + statusCode + ")";
                    break;
            }

            Log.e(TAG, errorMessage + ". Status code: " + statusCode, exception);
        } else {
            Log.e(TAG, "Unexpected error during Google Sign In", exception);
            if (exception != null && exception.getMessage() != null) {
                errorMessage += ": " + exception.getMessage();
            }
        }

        // Show the error message
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();

        // Show a dialog with more details for troubleshooting
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Google Sign In Failed")
            .setMessage(errorMessage + "\n\nPlease try the following:\n" +
                    "1. Check your internet connection\n" +
                    "2. Make sure Google Play Services is up to date\n" +
                    "3. Try using email login instead")
            .setPositiveButton("OK", null)
            .show();

        setLoadingState(false);
    }

    /**
     * Check if the user exists in Firestore and handle Google Sign-In
     * This method allows all employees added by the owner to log in with Google
     * even if they haven't set up Firebase Auth yet
     *
     * Updated to allow direct login for verified employees
     */
    private void checkIfUserExists(GoogleSignInAccount account) {
        setLoadingState(true);
        String email = account.getEmail();

        Log.d(TAG, "Checking if user exists in Firestore: " + email);

        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // User exists in Firestore, proceed with Firebase Auth
                        Log.d(TAG, "User exists in Firestore, proceeding with Firebase Auth");

                        // Get the user document
                        DocumentSnapshot userDoc = queryDocumentSnapshots.getDocuments().get(0);
                        String userRole = userDoc.getString("role");
                        String userName = userDoc.getString("name");
                        String userId = userDoc.getId();
                        Boolean isEmailVerified = userDoc.getBoolean("isEmailVerified");

                        Log.d(TAG, "User details - Name: " + userName + ", Role: " + userRole +
                                ", ID: " + userId + ", Verified: " + isEmailVerified);

                        // For employees, we'll now allow direct login with Google regardless of verification status
                        // This allows employees to use Google Sign-In immediately after being added by the owner

                        // If this is an employee account, we'll handle it specially
                        if ("employee".equals(userRole)) {
                            // Check if the email is verified or if we should verify it now
                            if (isEmailVerified == null || !isEmailVerified) {
                                Log.d(TAG, "Employee email not verified, updating status for Google login: " + email);

                                // Update verification status in Firestore and enable Google Authentication
                                db.collection("users")
                                    .document(userId)
                                    .update(
                                        "isEmailVerified", true,
                                        "isGoogleAuthenticated", true,
                                        "canUseGoogleAuth", true  // Enable Google Authentication
                                    )
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Updated verification status and enabled Google Auth for: " + email);

                                        // Proceed directly with Google authentication
                                        Log.d(TAG, "Proceeding directly with Google authentication after verification update");
                                        firebaseAuthWithGoogle(account.getIdToken());
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to update verification status", e);

                                        // Still try to proceed with Google authentication
                                        Log.d(TAG, "Still proceeding with Google authentication despite update failure");
                                        firebaseAuthWithGoogle(account.getIdToken());
                                    });
                            } else {
                                // Email is already verified, update Google authentication status and ensure canUseGoogleAuth is set
                                db.collection("users")
                                    .document(userId)
                                    .update(
                                        "isGoogleAuthenticated", true,
                                        "canUseGoogleAuth", true  // Ensure Google Authentication is enabled
                                    )
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Updated Google authentication status and enabled Google Auth for: " + email);

                                        // Proceed directly with Google authentication
                                        Log.d(TAG, "Proceeding directly with Google authentication for verified employee");
                                        firebaseAuthWithGoogle(account.getIdToken());
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to update Google authentication status", e);

                                        // Still try to proceed with Google authentication
                                        Log.d(TAG, "Still proceeding with Google authentication despite update failure");
                                        firebaseAuthWithGoogle(account.getIdToken());
                                    });
                            }

                            // Return early since we're handling authentication in the callbacks
                            return;
                        } else if ("owner".equals(userRole)) {
                            // For owner accounts, proceed directly with Google authentication
                            Log.d(TAG, "Owner account detected, proceeding directly with Google authentication");
                            firebaseAuthWithGoogle(account.getIdToken());
                            return;
                        }

                        // Check if this email is already registered with Firebase Auth
                        mAuth.fetchSignInMethodsForEmail(email)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    List<String> signInMethods = task.getResult().getSignInMethods();

                                    if (signInMethods != null && !signInMethods.isEmpty()) {
                                        // Email is already registered with Firebase Auth
                                        Log.d(TAG, "Email already registered with Firebase Auth, proceeding with Google sign-in");
                                        firebaseAuthWithGoogle(account.getIdToken());
                                    } else {
                                        // Email exists in Firestore but not in Firebase Auth
                                        // This means the owner added the employee but they haven't set up auth yet
                                        Log.d(TAG, "Email exists in Firestore but not in Firebase Auth, creating auth account");

                                        // Link this Google account with Firebase Auth
                                        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                                        mAuth.signInWithCredential(credential)
                                            .addOnCompleteListener(this, authTask -> {
                                                if (authTask.isSuccessful()) {
                                                    // Successfully created/linked Firebase Auth account
                                                    Log.d(TAG, "Successfully created/linked Firebase Auth account");
                                                    FirebaseUser user = mAuth.getCurrentUser();
                                                    if (user != null) {
                                                        // Check user role and redirect
                                                        checkUserRoleAndRedirect(user);
                                                    }
                                                } else {
                                                    // Failed to create/link Firebase Auth account
                                                    Log.e(TAG, "Failed to create/link Firebase Auth account", authTask.getException());
                                                    setLoadingState(false);
                                                    Toast.makeText(LoginActivity.this,
                                                        "Failed to set up your account with Google. Please try again or use email login.",
                                                        Toast.LENGTH_LONG).show();
                                                }
                                            });
                                    }
                                } else {
                                    // Failed to check sign-in methods
                                    Log.e(TAG, "Failed to check sign-in methods", task.getException());
                                    setLoadingState(false);
                                    Toast.makeText(LoginActivity.this,
                                        "Failed to verify your account. Please try again.",
                                        Toast.LENGTH_LONG).show();
                                }
                            });
                    } else {
                        // User doesn't exist in Firestore
                        setLoadingState(false);
                        Log.d(TAG, "User does not exist in Firestore: " + email);

                        // Sign out from Google to clear the account
                        mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                            Toast.makeText(LoginActivity.this,
                                "This Google account is not registered. Please contact the owner to add your account.",
                                Toast.LENGTH_LONG).show();

                            // Show a more detailed dialog
                            new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                                .setTitle("Account Not Found")
                                .setMessage("The Google account (" + email + ") is not registered in the system.\n\n" +
                                        "Only employees added by the owner can access the app. Please contact your manager to add your account.")
                                .setPositiveButton("OK", null)
                                .show();
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    setLoadingState(false);
                    Log.e(TAG, "Error checking user existence", e);
                    Toast.makeText(LoginActivity.this,
                        "Error checking user account: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Check if the employee's email is verified and handle accordingly
     */
    private void checkEmailVerificationStatus(String email, GoogleSignInAccount account) {
        // Use the EmailVerificationChecker to check verification status
        EmailVerificationChecker.checkVerificationAndSendCredentials(email, new EmailVerificationChecker.VerificationCallback() {
            @Override
            public void onResult(boolean isVerified, String message) {
                if (isVerified) {
                    // Email is verified, proceed with Google sign-in
                    Log.d(TAG, "Email is verified, proceeding with Google sign-in");
                    firebaseAuthWithGoogle(account.getIdToken());
                } else {
                    // Email is not verified, show message
                    setLoadingState(false);
                    Log.d(TAG, "Email not verified: " + message);

                    // Sign out from Google
                    mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                        // Show verification required dialog
                        new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Email Verification Required")
                            .setMessage("Your email address (" + email + ") has not been verified yet.\n\n" +
                                    "Please check your email for a verification link and verify your email before logging in.\n\n" +
                                    "Once verified, you will receive your login credentials.")
                            .setPositiveButton("OK", null)
                            .show();
                    });
                }
            }
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        Log.d(TAG, "Starting Firebase authentication with Google token");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase Auth with Google successful");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Check user role and redirect accordingly
                            checkUserRoleAndRedirect(user);
                        }
                    } else {
                        setLoadingState(false);
                        Exception exception = task.getException();
                        Log.e(TAG, "signInWithCredential:failure", exception);

                        final String errorMessage = "Authentication failed" +
                            ((exception != null && exception.getMessage() != null) ? ": " + exception.getMessage() : "");

                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();

                        // Sign out from Google to clear any cached credentials
                        mGoogleSignInClient.signOut().addOnCompleteListener(signOutTask -> {
                            // Show a dialog with more details
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Google Sign In Failed")
                                .setMessage("There was a problem signing in with your Google account. Please try again or use email login.\n\n" +
                                        "Error: " + errorMessage)
                                .setPositiveButton("OK", null)
                                .show();
                        });
                    }
                });
    }

    private void setLoadingState(boolean isLoading) {
        loginButton.setEnabled(!isLoading);
        googleSignInButton.setEnabled(!isLoading);
        if (isLoading) {
            Toast.makeText(this, "Please wait...", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show a detailed error dialog for Google Sign-In developer errors (status code 10)
     * This provides more helpful information to fix the configuration issue
     */
    private void showDeveloperErrorDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Google Sign-In Configuration Error")
            .setMessage("There's a mismatch between your app's configuration and Google Cloud settings. This is typically caused by:\n\n" +
                    "1. Incorrect SHA-1 fingerprint in Firebase Console\n" +
                    "2. Package name mismatch\n" +
                    "3. Incorrect OAuth client ID\n\n" +
                    "To fix this:\n" +
                    "• Verify the SHA-1 fingerprint in Firebase Console matches your app's signing certificate\n" +
                    "• Ensure the package name in Firebase Console matches your app's package name\n" +
                    "• Regenerate the google-services.json file and update it in your app\n\n" +
                    "Would you like to try email login instead?")
            .setPositiveButton("Use Email Login", (dialog, which) -> {
                // Focus on the email field
                if (emailEditText != null) {
                    emailEditText.requestFocus();
                }
            })
            .setNegativeButton("Try Again", (dialog, which) -> {
                // Try to reinitialize Google Sign-In
                initializeGoogleSignIn();
                if (mGoogleSignInClient != null) {
                    showGoogleSignInUI();
                }
            })
            .setCancelable(false)
            .show();
    }



    /**
     * Verifies that the user has the owner role in Firestore
     * Updated to allow employees to log in directly with Google authentication
     */
    private void checkUserRoleAndRedirect(FirebaseUser user) {
        Log.d(TAG, "Checking user role for: " + user.getEmail());

        // Show loading state
        setLoadingState(true);

        // Check the user's role in Firestore
        db.collection("users")
            .document(user.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                setLoadingState(false);

                if (documentSnapshot.exists()) {
                    String role = documentSnapshot.getString("role");
                    Log.d(TAG, "User role: " + role);

                    if ("owner".equals(role)) {
                        // User is an owner, redirect to owner dashboard
                        Log.d(TAG, "Redirecting to Owner Dashboard");
                        startActivity(new Intent(LoginActivity.this, OwnerDashboardActivity.class));
                        finish();
                    } else if ("employee".equals(role)) {
                        // User is an employee
                        Boolean isEmailVerified = documentSnapshot.getBoolean("isEmailVerified");

                        // If using Google Sign-In, we'll consider the email verified
                        // This allows employees to log in directly with Google
                        if (user.getProviderData() != null) {
                            for (UserInfo profile : user.getProviderData()) {
                                // Check if the user signed in with Google
                                if (GoogleAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                                    Log.d(TAG, "User signed in with Google, marking as verified");

                                    // Update verification status if needed
                                    if (isEmailVerified == null || !isEmailVerified) {
                                        db.collection("users")
                                            .document(user.getUid())
                                            .update(
                                                "isEmailVerified", true,
                                                "isGoogleAuthenticated", true,
                                                "canUseGoogleAuth", true  // Ensure Google Authentication is enabled
                                            )
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "Updated verification status for Google user: " + user.getEmail());
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Failed to update verification status", e);
                                            });
                                    }

                                    // Set as verified for this session
                                    isEmailVerified = true;

                                    // Update Google authentication status and ensure canUseGoogleAuth is set
                                    db.collection("users")
                                        .document(user.getUid())
                                        .update(
                                            "isGoogleAuthenticated", true,
                                            "canUseGoogleAuth", true  // Ensure Google Authentication is enabled
                                        )
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Updated Google authentication status");
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to update Google authentication status", e);
                                        });

                                    break;
                                }
                            }
                        }

                        if (isEmailVerified != null && isEmailVerified) {
                            // Email is verified, redirect to employee dashboard
                            Log.d(TAG, "Redirecting to Employee Dashboard");
                            startActivity(new Intent(LoginActivity.this, EmployeeDashboardActivity.class));
                            finish();
                        } else {
                            // Email not verified, show verification dialog
                            Log.d(TAG, "Employee email not verified");
                            Toast.makeText(LoginActivity.this,
                                "Your email needs to be verified before you can log in. Please check your email for a verification link.",
                                Toast.LENGTH_LONG).show();
                            mAuth.signOut();

                            // Show a more detailed dialog
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Email Verification Required")
                                .setMessage("Your email address needs to be verified before you can log in.\n\n" +
                                        "Please check your email for a verification link. After verifying your email, " +
                                        "you can log in with either your email/password or Google account.")
                                .setPositiveButton("OK", null)
                                .show();
                        }
                    } else {
                        // Unknown role
                        Log.e(TAG, "Unknown user role: " + role);
                        Toast.makeText(this,
                            "Account has an invalid role. Please contact support.",
                            Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                    }
                } else {
                    // Special case for owner@minuteburgers.com
                    if ("owner@minuteburgers.com".equals(user.getEmail())) {
                        Log.d(TAG, "Creating owner profile for default owner account");
                        createOwnerProfile(user.getUid());
                    } else {
                        Log.e(TAG, "User document does not exist in Firestore");
                        Toast.makeText(this,
                            "Account not found. Please contact support.",
                            Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                    }
                }
            })
            .addOnFailureListener(e -> {
                setLoadingState(false);
                Log.e(TAG, "Error checking user role", e);
                Toast.makeText(this,
                    "Error checking user role: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
                mAuth.signOut();
            });
    }

    /**
     * Creates the owner profile in Firestore
     */
    private void createOwnerProfile(String userId) {
        Log.d(TAG, "Creating owner profile in Firestore");

        // Show loading state
        setLoadingState(true);

        Map<String, Object> ownerData = new HashMap<>();
        ownerData.put("name", "Restaurant Owner");
        ownerData.put("email", "owner@minuteburgers.com");
        ownerData.put("role", "owner");
        ownerData.put("branch", "Main Branch");
        ownerData.put("registrationDate", new Date());
        ownerData.put("isActive", true);

        // Add owner permissions
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("manageInventory", true);
        permissions.put("manageStaff", true);
        permissions.put("viewReports", true);
        permissions.put("approveRequests", true);
        permissions.put("accessOwnerDashboard", true);
        ownerData.put("permissions", permissions);

        db.collection("users")
            .document(userId)
            .set(ownerData)
            .addOnSuccessListener(aVoid -> {
                setLoadingState(false);
                Log.d(TAG, "Owner profile created successfully");

                // Proceed to owner dashboard
                startActivity(new Intent(LoginActivity.this, OwnerDashboardActivity.class));
                finish();
            })
            .addOnFailureListener(e -> {
                setLoadingState(false);
                Log.e(TAG, "Error creating owner profile", e);
                Toast.makeText(this,
                    "Error creating owner profile: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();

                // Sign out the user
                mAuth.signOut();
            });
    }

    /**
     * Force creates the owner account in Firebase Auth and Firestore
     */
    private void forceCreateOwnerAccount() {
        Log.d(TAG, "Force creating owner account");

        // Show a confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create Owner Account")
            .setMessage("This will create or update the owner account with email: owner@minuteburgers.com and password: owner123456")
            .setPositiveButton("Create", (dialog, which) -> {
                // Show loading state
                setLoadingState(true);

                // Create the owner account
                String email = "owner@minuteburgers.com";
                String password = "owner123456";

                // First check if the email is already registered
                mAuth.fetchSignInMethodsForEmail(email)
                    .addOnSuccessListener(signInMethodsResult -> {
                        List<String> signInMethods = signInMethodsResult.getSignInMethods();

                        if (signInMethods != null && !signInMethods.isEmpty()) {
                            // Email is already registered, try to sign in
                            Log.d(TAG, "Owner email already registered, attempting to sign in");

                            mAuth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener(authResult -> {
                                    // Successfully signed in, now create/update the Firestore profile
                                    FirebaseUser user = authResult.getUser();
                                    if (user != null) {
                                        Log.d(TAG, "Successfully signed in as owner: " + user.getUid());

                                        // Create or update the owner profile in Firestore
                                        createOwnerProfileInFirestore(user.getUid());
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to sign in as owner: " + e.getMessage(), e);

                                    // Try to create a new user with the same email
                                    tryDeleteAndRecreateOwner(email, password);
                                });
                        } else {
                            // Email is not registered, create a new account
                            Log.d(TAG, "Owner email not registered, creating new account");

                            mAuth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener(authResult -> {
                                    FirebaseUser user = authResult.getUser();
                                    if (user != null) {
                                        Log.d(TAG, "Owner account created successfully: " + user.getUid());

                                        // Create the owner profile in Firestore
                                        createOwnerProfileInFirestore(user.getUid());
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create owner account: " + e.getMessage(), e);
                                    setLoadingState(false);
                                    Toast.makeText(this, "Failed to create owner account: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking email registration: " + e.getMessage(), e);
                        setLoadingState(false);
                        Toast.makeText(this, "Error checking email registration: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Try to delete and recreate the owner account
     */
    private void tryDeleteAndRecreateOwner(String email, String password) {
        Log.d(TAG, "Attempting to reset owner password");

        // Send password reset email
        mAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Password reset email sent successfully");
                Toast.makeText(this,
                    "Password reset email sent to " + email + ". Please check your email and set a new password.",
                    Toast.LENGTH_LONG).show();
                setLoadingState(false);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error sending password reset email", e);
                Toast.makeText(this,
                    "Error sending password reset email: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
                setLoadingState(false);
            });
    }

    /**
     * Creates the owner profile in Firestore
     */
    private void createOwnerProfileInFirestore(String userId) {
        Log.d(TAG, "Creating owner profile in Firestore");

        // Create a complete owner data map
        Map<String, Object> ownerData = new HashMap<>();
        ownerData.put("uid", userId);
        ownerData.put("name", "Restaurant Owner");
        ownerData.put("email", "owner@minuteburgers.com");
        ownerData.put("role", "owner");
        ownerData.put("branch", "Main Branch");
        ownerData.put("registrationDate", new Date());
        ownerData.put("lastUpdated", new Date());
        ownerData.put("isActive", true);

        // Add owner permissions
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("manageInventory", true);
        permissions.put("manageStaff", true);
        permissions.put("viewReports", true);
        permissions.put("approveRequests", true);
        permissions.put("accessOwnerDashboard", true);
        ownerData.put("permissions", permissions);

        db.collection("users")
            .document(userId)
            .set(ownerData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Owner profile created successfully");

                // Sign out after creating profile
                mAuth.signOut();

                // Auto-fill the owner credentials
                TextInputEditText emailEditText = findViewById(R.id.emailEditText);
                TextInputEditText passwordEditText = findViewById(R.id.passwordEditText);
                emailEditText.setText("owner@minuteburgers.com");
                passwordEditText.setText("owner123456");

                setLoadingState(false);
                Toast.makeText(this, "Owner account created successfully. You can now log in.", Toast.LENGTH_LONG).show();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error creating owner profile", e);
                Toast.makeText(this, "Error creating owner profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                setLoadingState(false);

                // Sign out
                mAuth.signOut();
            });
    }

    /**
     * Initialize Google Sign-In client
     * This method handles the initialization of the Google Sign-In client
     * and ensures it's properly configured
     */
    private void initializeGoogleSignIn() {
        try {
            // First check if Google Play Services is available
            com.google.android.gms.common.GoogleApiAvailability apiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                if (apiAvailability.isUserResolvableError(resultCode)) {
                    Log.e(TAG, "Google Play Services not available but resolvable: " + resultCode);
                    apiAvailability.getErrorDialog(this, resultCode, 9000).show();
                    // Disable Google Sign-In button
                    if (googleSignInButton != null) {
                        googleSignInButton.setEnabled(false);
                    }
                    return;
                } else {
                    Log.e(TAG, "Google Play Services not available and not resolvable: " + resultCode);
                    Toast.makeText(this, "This device doesn't support Google Sign In", Toast.LENGTH_LONG).show();
                    // Disable Google Sign-In button
                    if (googleSignInButton != null) {
                        googleSignInButton.setEnabled(false);
                    }
                    return;
                }
            }

            // Use the correct web client ID directly
            // This is the value from the logs that shows it's working
            String webClientId = "469900893337-tcbjt8bh4k18kqe50brc70m29b0su526.apps.googleusercontent.com";
            Log.d(TAG, "Using correct web client ID: " + webClientId);

            Log.d(TAG, "Initializing Google Sign-In with web client ID: " + webClientId);

            // Log the SHA-1 fingerprint for debugging
            try {
                android.content.pm.PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
                for (android.content.pm.Signature signature : packageInfo.signatures) {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                    md.update(signature.toByteArray());
                    String sha1 = android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP);
                    Log.d(TAG, "App SHA-1 fingerprint: " + sha1);

                    // Also log the hex format that Firebase Console uses
                    byte[] hashBytes = md.digest();
                    StringBuilder hexString = new StringBuilder();
                    for (byte hashByte : hashBytes) {
                        String hex = Integer.toHexString(0xff & hashByte);
                        if (hex.length() == 1) {
                            hexString.append('0');
                        }
                        hexString.append(hex);
                    }
                    // Log the SHA-1 fingerprint but don't enforce a match
                    // This allows the app to work with different signing keys
                    Log.d(TAG, "App SHA-1 fingerprint (hex): " + hexString.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting SHA-1 fingerprint", e);
            }

            // Create Google Sign-In options with simplified settings
            // Remove .requestProfile() which might be causing issues
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();

            // Initialize the client
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

            if (mGoogleSignInClient == null) {
                Log.e(TAG, "Failed to create Google Sign-In client");
                Toast.makeText(this, "Failed to initialize Google Sign-In", Toast.LENGTH_LONG).show();
                // Disable Google Sign-In button
                if (googleSignInButton != null) {
                    googleSignInButton.setEnabled(false);
                }
                return;
            }

            Log.d(TAG, "Google Sign In configured successfully");

            // Check if we already have a signed-in Google account
            GoogleSignInAccount lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this);
            if (lastSignedInAccount != null) {
                Log.d(TAG, "Found previously signed-in Google account: " + lastSignedInAccount.getEmail());
            }

            // Enable Google Sign-In button if it's already initialized
            if (googleSignInButton != null) {
                googleSignInButton.setEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Google Sign In: ", e);
            Toast.makeText(this, "Error initializing Google Sign In: " + e.getMessage(), Toast.LENGTH_LONG).show();
            // Disable Google Sign-In button
            if (googleSignInButton != null) {
                googleSignInButton.setEnabled(false);
            }
        }
    }

    /**
     * Checks if Google Services JSON is properly configured
     * and if Google Play Services is available
     * Updated to provide more detailed diagnostics
     */
    private void checkGoogleServicesConfig() {
        try {
            // Try to get the default_web_client_id resource
            int resourceId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
            if (resourceId == 0) {
                Log.e(TAG, "Google Services configuration error: default_web_client_id not found");

                // Check if google-services.json exists and has the correct client ID
                try {
                    // Get the client ID from google-services.json (hardcoded as fallback)
                    String hardcodedClientId = "469900893337-tcbjt8bh4k18kqe50brc70m29b0su526.apps.googleusercontent.com";
                    Log.d(TAG, "Using hardcoded client ID from google-services.json: " + hardcodedClientId);

                    // Show a more detailed error message
                    Toast.makeText(this,
                        "Google Sign In configuration issue: default_web_client_id not found. Using fallback.",
                        Toast.LENGTH_LONG).show();

                    // Continue with initialization using the hardcoded value
                    return;
                } catch (Exception ex) {
                    Log.e(TAG, "Error checking google-services.json", ex);
                    Toast.makeText(this,
                        "Google Sign In is not properly configured. Please check Firebase setup.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
            }

            String webClientId = getString(resourceId);
            Log.d(TAG, "Found web client ID in resources: " + webClientId);

            if (webClientId == null || webClientId.isEmpty() || webClientId.equals("YOUR_WEB_CLIENT_ID")) {
                Log.e(TAG, "Google Services configuration error: invalid default_web_client_id");

                // Use the hardcoded value from google-services.json
                String hardcodedClientId = "469900893337-tcbjt8bh4k18kqe50brc70m29b0su526.apps.googleusercontent.com";
                Log.d(TAG, "Using hardcoded client ID from google-services.json: " + hardcodedClientId);

                Toast.makeText(this,
                    "Google Sign In configuration issue: invalid web client ID. Using fallback.",
                    Toast.LENGTH_LONG).show();
                return;
            }

            // Verify that the web client ID matches the one in google-services.json
            String expectedClientId = "469900893337-tcbjt8bh4k18kqe50brc70m29b0su526.apps.googleusercontent.com";
            if (!webClientId.equals(expectedClientId)) {
                Log.e(TAG, "Web client ID mismatch. Expected: " + expectedClientId + ", Found: " + webClientId);

                // Update the resource value at runtime (this won't persist)
                Toast.makeText(this,
                    "Google Sign In configuration issue: web client ID mismatch. Using correct value.",
                    Toast.LENGTH_LONG).show();
            }

            // Check if Google Play Services is available
            com.google.android.gms.common.GoogleApiAvailability apiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                if (apiAvailability.isUserResolvableError(resultCode)) {
                    Log.e(TAG, "Google Play Services not available but resolvable: " + resultCode);
                    apiAvailability.getErrorDialog(this, resultCode, 9000).show();
                } else {
                    Log.e(TAG, "Google Play Services not available and not resolvable: " + resultCode);
                    Toast.makeText(this, "This device doesn't support Google Sign In", Toast.LENGTH_LONG).show();
                }
            } else {
                Log.d(TAG, "Google Play Services is available");
            }

            Log.d(TAG, "Google Services configuration check passed. Web Client ID found.");
        } catch (Exception e) {
            Log.e(TAG, "Error checking Google Services configuration", e);
            Toast.makeText(this,
                "Error checking Google Services configuration: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
}