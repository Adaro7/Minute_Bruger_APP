package com.example.minuteburgers;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;

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

        // Configure Google Sign In
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            Log.d(TAG, "Google Sign In configured successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Sign In: ", e);
            Toast.makeText(this, "Error configuring Google Sign In", Toast.LENGTH_SHORT).show();
        }

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
        signUpTextView.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Sign out from Google to show account picker every time
        mGoogleSignInClient.signOut();
    }

    private void handleEmailLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        setLoadingState(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoadingState(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleGoogleSignIn() {
        try {
            Log.d(TAG, "Starting Google Sign In process");
            // Sign out first to always show account picker
            mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting Google Sign In: ", e);
            Toast.makeText(this, "Error starting Google Sign In: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "Google Sign In result received. Result code: " + resultCode);
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google Sign In successful. Email: " + account.getEmail());
                // Check if this Google account is registered
                checkIfUserExists(account);
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed. Error code: " + e.getStatusCode(), e);
                Toast.makeText(this, "Google sign in failed. Error code: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
                setLoadingState(false);
            }
        }
    }

    private void checkIfUserExists(GoogleSignInAccount account) {
        setLoadingState(true);
        String email = account.getEmail();
        
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // User exists, proceed with sign in
                        firebaseAuthWithGoogle(account.getIdToken());
                    } else {
                        // User doesn't exist
                        setLoadingState(false);
                        Toast.makeText(LoginActivity.this, 
                            "This Google account is not registered. Please sign up first.", 
                            Toast.LENGTH_LONG).show();
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

    private void firebaseAuthWithGoogle(String idToken) {
        Log.d(TAG, "Starting Firebase authentication with Google token");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase Auth with Google successful");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // User is signed in, proceed to main activity
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    } else {
                        setLoadingState(false);
                        Log.e(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
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
} 