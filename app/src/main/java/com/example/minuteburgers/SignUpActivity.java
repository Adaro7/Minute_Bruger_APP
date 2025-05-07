package com.example.minuteburgers;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    private static final String TAG = "SignUpActivity";
    private static final int RC_SIGN_IN = 9001;

    private TextInputEditText nameEditText, emailEditText, passwordEditText;
    private MaterialButton signUpButton;
    private SignInButton googleSignUpButton;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Firebase Auth and Firestore
        try {
            mAuth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();

            // Configure Google Sign In
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            Log.d(TAG, "Firebase and Google Sign In initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase:", e);
            Toast.makeText(this, "Error initializing Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Initialize views
        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        signUpButton = findViewById(R.id.signUpButton);
        googleSignUpButton = findViewById(R.id.googleSignUpButton);
        progressBar = findViewById(R.id.progressBar);

        // Set click listeners
        signUpButton.setOnClickListener(v -> handleEmailSignUp());
        googleSignUpButton.setOnClickListener(v -> handleGoogleSignUp());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Sign out from Google to show account picker every time
        mGoogleSignInClient.signOut();
    }

    private void showLoading(boolean show) {
        signUpButton.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        googleSignUpButton.setEnabled(!show);
        nameEditText.setEnabled(!show);
        emailEditText.setEnabled(!show);
        passwordEditText.setEnabled(!show);
    }

    private void handleEmailSignUp() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validate inputs
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        showLoading(true);
        Log.d(TAG, "Starting user creation process for email: " + email);

        // Check if email already exists in Firestore
        checkIfUserExists(email, exists -> {
            if (exists) {
                showLoading(false);
                Toast.makeText(SignUpActivity.this, 
                    "This email is already registered. Please log in instead.", 
                    Toast.LENGTH_LONG).show();
                return;
            }

            // Create user with email and password
            createUserWithEmail(name, email, password);
        });
    }

    private void handleGoogleSignUp() {
        try {
            Log.d(TAG, "Starting Google Sign Up process");
            // Sign out first to always show account picker
            mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting Google Sign Up: ", e);
            Toast.makeText(this, "Error starting Google Sign Up: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google Sign In successful. Email: " + account.getEmail());
                
                // Check if the Google account is already registered
                checkIfUserExists(account.getEmail(), exists -> {
                    if (exists) {
                        showLoading(false);
                        Toast.makeText(SignUpActivity.this, 
                            "This Google account is already registered. Please log in instead.", 
                            Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Create new user with Google account
                    firebaseAuthWithGoogle(account.getIdToken(), account.getDisplayName());
                });
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        }
    }

    private void checkIfUserExists(String email, UserExistsCallback callback) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    callback.onResult(!queryDocumentSnapshots.isEmpty());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user existence", e);
                    Toast.makeText(SignUpActivity.this, 
                        "Error checking user account: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                    callback.onResult(false);
                });
    }

    private void createUserWithEmail(String name, String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            createUserProfile(user.getUid(), name, email);
                        }
                    } else {
                        showLoading(false);
                        Log.e(TAG, "createUserWithEmail:failure", task.getException());
                        String errorMessage = task.getException() != null ? 
                            task.getException().getMessage() : "Unknown error occurred";
                        Toast.makeText(SignUpActivity.this,
                                "Registration failed: " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken, String name) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            createUserProfile(user.getUid(), name, user.getEmail());
                        }
                    } else {
                        showLoading(false);
                        Log.e(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(SignUpActivity.this, 
                            "Authentication failed: " + task.getException().getMessage(),
                            Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createUserProfile(String userId, String name, String email) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("name", name);
        userProfile.put("email", email);
        userProfile.put("role", "staff");
        userProfile.put("registrationDate", new Date());

        db.collection("users")
                .document(userId)
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile created for: " + email);
                    showLoading(false);
                    Toast.makeText(SignUpActivity.this,
                            "Account created successfully!",
                            Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SignUpActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error creating user profile", e);
                    Toast.makeText(SignUpActivity.this, 
                        "Error creating user profile: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
    }

    private interface UserExistsCallback {
        void onResult(boolean exists);
    }
}

 