package com.example.minuteburgers;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {
    private static final String TAG = "EditProfileActivity";
    
    private TextInputEditText nameInput, emailInput;
    private AutoCompleteTextView branchInput;
    private MaterialButton saveButton, backButton, changePasswordButton;
    private LinearProgressIndicator progressIndicator;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        initializeViews();
        
        // Load user profile
        loadUserProfile();
    }
    
    private void initializeViews() {
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        branchInput = findViewById(R.id.branchDropdown);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        progressIndicator = findViewById(R.id.progressIndicator);
        
        // Setup branch dropdown
        String[] branches = {"Main Branch", "North Branch", "South Branch", "East Branch", "West Branch"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, branches);
        branchInput.setAdapter(adapter);
        
        // Setup click listeners
        saveButton.setOnClickListener(v -> saveProfile());
        backButton.setOnClickListener(v -> finish());
        changePasswordButton.setOnClickListener(v -> {
            // Open change password activity
            startActivity(new android.content.Intent(this, ChangePasswordActivity.class));
        });
        
        // Email should not be editable
        emailInput.setEnabled(false);
    }
    
    private void loadUserProfile() {
        showLoading(true);
        
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    
                    if (documentSnapshot.exists()) {
                        // Populate fields with user data
                        nameInput.setText(documentSnapshot.getString("name"));
                        emailInput.setText(documentSnapshot.getString("email"));
                        branchInput.setText(documentSnapshot.getString("branch"), false);
                    } else {
                        Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error loading user profile", e);
                    Toast.makeText(this, "Error loading profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
    
    private void saveProfile() {
        String name = nameInput.getText().toString().trim();
        String branch = branchInput.getText().toString().trim();
        
        if (name.isEmpty() || branch.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);
        
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Update profile in Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("branch", branch);
        
        db.collection("users").document(user.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error updating profile", e);
                    Toast.makeText(EditProfileActivity.this, "Error updating profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!show);
        nameInput.setEnabled(!show);
        branchInput.setEnabled(!show);
    }
}
