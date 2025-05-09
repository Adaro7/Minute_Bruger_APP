package com.example.minuteburgers;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {
    private static final String TAG = "ChangePasswordActivity";
    
    private TextInputEditText currentPasswordInput, newPasswordInput, confirmPasswordInput;
    private MaterialButton changePasswordButton, cancelButton;
    private LinearProgressIndicator progressIndicator;
    
    private FirebaseAuth mAuth;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize views
        initializeViews();
        
        // Check if user is logged in
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please log in to change your password", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initializeViews() {
        currentPasswordInput = findViewById(R.id.currentPasswordInput);
        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressIndicator = findViewById(R.id.progressIndicator);
        
        // Setup click listeners
        changePasswordButton.setOnClickListener(v -> validateAndChangePassword());
        cancelButton.setOnClickListener(v -> finish());
    }
    
    private void validateAndChangePassword() {
        String currentPassword = currentPasswordInput.getText().toString().trim();
        String newPassword = newPasswordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();
        
        // Validate inputs
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (newPassword.length() < 6) {
            Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Change password
        changePassword(currentPassword, newPassword);
    }
    
    private void changePassword(String currentPassword, String newPassword) {
        showLoading(true);
        
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Re-authenticate user
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // User re-authenticated, now change password
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(aVoid1 -> {
                                showLoading(false);
                                Toast.makeText(ChangePasswordActivity.this, 
                                        "Password changed successfully", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Log.e(TAG, "Error changing password", e);
                                Toast.makeText(ChangePasswordActivity.this, 
                                        "Error changing password: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error re-authenticating user", e);
                    Toast.makeText(ChangePasswordActivity.this, 
                            "Current password is incorrect", 
                            Toast.LENGTH_SHORT).show();
                });
    }
    
    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        changePasswordButton.setEnabled(!show);
        cancelButton.setEnabled(!show);
        currentPasswordInput.setEnabled(!show);
        newPasswordInput.setEnabled(!show);
        confirmPasswordInput.setEnabled(!show);
    }
}
