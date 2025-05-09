package com.example.minuteburgers;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.minuteburgers.models.StockRequest;
import com.example.minuteburgers.models.User;
import com.example.minuteburgers.utils.EmailSender;
import com.example.minuteburgers.utils.PermissionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class StockRequestActivity extends AppCompatActivity {
    private static final String TAG = "StockRequestActivity";

    private TextInputEditText quantityEditText, notesEditText;
    private AutoCompleteTextView itemDropdown;
    private MaterialButton submitButton, cancelButton;
    private LinearProgressIndicator progressIndicator;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DatabaseReference inventoryRef;
    private List<String> inventoryItems = new ArrayList<>();
    private String selectedItem;
    private User currentUser;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_request);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        inventoryRef = FirebaseDatabase.getInstance().getReference("inventory");

        // Initialize permission manager
        permissionManager = PermissionManager.getInstance();

        // Initialize views
        initializeViews();

        // Load inventory items for dropdown
        loadInventoryItems();

        // Load current user data
        loadCurrentUserData();
    }

    private void initializeViews() {
        itemDropdown = findViewById(R.id.itemDropdown);
        quantityEditText = findViewById(R.id.quantityEditText);
        notesEditText = findViewById(R.id.notesEditText);
        submitButton = findViewById(R.id.submitButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressIndicator = findViewById(R.id.progressIndicator);

        // Set click listeners
        submitButton.setOnClickListener(v -> handleSubmit());
        cancelButton.setOnClickListener(v -> finish());

        // Set item selection listener
        itemDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedItem = inventoryItems.get(position);
            Log.d(TAG, "Selected item: " + selectedItem);
        });
    }

    private void loadInventoryItems() {
        showLoading(true);
        inventoryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                inventoryItems.clear();
                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    String itemName = itemSnapshot.child("name").getValue(String.class);
                    if (itemName != null && !itemName.isEmpty()) {
                        inventoryItems.add(itemName);
                    }
                }

                // Setup dropdown with inventory items
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        StockRequestActivity.this,
                        android.R.layout.simple_dropdown_item_1line,
                        inventoryItems);
                itemDropdown.setAdapter(adapter);

                showLoading(false);

                if (inventoryItems.isEmpty()) {
                    Toast.makeText(StockRequestActivity.this,
                            "No inventory items found",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Error loading inventory items", error.toException());
                Toast.makeText(StockRequestActivity.this,
                        "Error loading inventory items",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCurrentUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Load permissions first
            permissionManager.loadUserPermissions(mAuth, db, success -> {
                if (success) {
                    // Check if user has permission to request stock
                    if (!permissionManager.hasPermission(PermissionManager.REQUEST_STOCK)) {
                        Toast.makeText(StockRequestActivity.this,
                                "You don't have permission to request stock",
                                Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "User role: " + permissionManager.getUserRole() +
                              " does not have permission to request stock");
                        finish();
                        return;
                    }

                    // Load user details
                    db.collection("users").document(user.getUid())
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String name = documentSnapshot.getString("name");
                                    String email = documentSnapshot.getString("email");
                                    String role = documentSnapshot.getString("role");
                                    String branch = documentSnapshot.getString("branch");

                                    currentUser = new User(user.getUid(), name, email, role, branch);
                                    Log.d(TAG, "User data loaded: " + name + ", " + role + ", " + branch);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error loading user data", e);
                                Toast.makeText(StockRequestActivity.this,
                                        "Error loading user data",
                                        Toast.LENGTH_SHORT).show();
                            });
                } else {
                    // Failed to load permissions
                    Log.e(TAG, "Failed to load permissions");
                    Toast.makeText(StockRequestActivity.this,
                            "Error checking permissions",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        } else {
            // User not logged in
            Toast.makeText(this, "Please log in to continue", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void handleSubmit() {
        if (currentUser == null) {
            Toast.makeText(this, "User data not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate inputs
        if (selectedItem == null || selectedItem.isEmpty()) {
            Toast.makeText(this, "Please select an item", Toast.LENGTH_SHORT).show();
            return;
        }

        String quantityStr = quantityEditText.getText().toString().trim();
        if (quantityStr.isEmpty()) {
            Toast.makeText(this, "Please enter quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                Toast.makeText(this, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        String notes = notesEditText.getText().toString().trim();

        // Create stock request
        StockRequest request = new StockRequest(
                selectedItem,
                quantity,
                currentUser.getUid(),
                currentUser.getName(),
                currentUser.getEmail(),
                currentUser.getBranch());

        if (!notes.isEmpty()) {
            request.setNotes(notes);
        }

        // Save to Firebase
        saveStockRequest(request);
    }

    private void saveStockRequest(StockRequest request) {
        showLoading(true);

        DatabaseReference requestsRef = FirebaseDatabase.getInstance().getReference("stockRequests");
        String requestId = requestsRef.push().getKey();

        if (requestId != null) {
            request.setId(requestId);
            requestsRef.child(requestId).setValue(request)
                    .addOnSuccessListener(aVoid -> {
                        showLoading(false);
                        Toast.makeText(StockRequestActivity.this,
                                "Stock request submitted successfully",
                                Toast.LENGTH_SHORT).show();

                        // Send email notification to owner
                        EmailSender.findOwnerEmailAndSendRequest(StockRequestActivity.this, request);

                        finish();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Log.e(TAG, "Error saving stock request", e);
                        Toast.makeText(StockRequestActivity.this,
                                "Error submitting request: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        } else {
            showLoading(false);
            Toast.makeText(this, "Error generating request ID", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!show);
        cancelButton.setEnabled(!show);
        itemDropdown.setEnabled(!show);
        quantityEditText.setEnabled(!show);
        notesEditText.setEnabled(!show);
    }
}
