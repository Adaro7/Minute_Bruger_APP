package com.example.minuteburgers;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.minuteburgers.fragments.AnnouncementsFragment;
import com.example.minuteburgers.fragments.InventoryFragment;
import com.example.minuteburgers.models.StockRequest;
import com.example.minuteburgers.utils.EmailSender;
import com.example.minuteburgers.utils.PermissionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmployeeDashboardActivity extends AppCompatActivity implements InventoryFragment.ItemDetailListener {
    private static final String TAG = "EmployeeDashboardActivity";
    private static final int NUM_PAGES = 2;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DatabaseReference inventoryRef;
    private PermissionManager permissionManager;

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private TextView userNameText;
    private ImageButton logoutButton;
    private ExtendedFloatingActionButton requestStockButton;
    private LinearProgressIndicator progressIndicator;

    private List<String> inventoryItems = new ArrayList<>();
    private String selectedItem;
    private String currentUserName;
    private String currentUserEmail;
    private String currentUserBranch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_dashboard);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        inventoryRef = FirebaseDatabase.getInstance().getReference("inventory");

        // Initialize permission manager
        permissionManager = PermissionManager.getInstance();

        // Check if user is logged in
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Initialize views
        initializeViews();

        // Setup ViewPager and TabLayout
        setupViewPager();

        // Load user data
        loadUserData();

        // Load inventory items for stock request
        loadInventoryItems();
    }

    private void initializeViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        userNameText = findViewById(R.id.userNameText);
        logoutButton = findViewById(R.id.logoutButton);
        requestStockButton = findViewById(R.id.requestStockButton);
        progressIndicator = findViewById(R.id.progressIndicator);

        // Set click listeners
        logoutButton.setOnClickListener(v -> handleLogout());
        requestStockButton.setOnClickListener(v -> showStockRequestDialog());
    }

    private void setupViewPager() {
        // Create adapter
        EmployeePagerAdapter pagerAdapter = new EmployeePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Connect TabLayout with ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Inventory");
                    break;
                case 1:
                    tab.setText("Announcements");
                    break;
            }
        }).attach();
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Load permissions first
            permissionManager.loadUserPermissions(mAuth, db, success -> {
                if (success) {
                    // Check if user has employee role
                    if (!"employee".equals(permissionManager.getUserRole())) {
                        // Redirect to owner dashboard if user is owner
                        startActivity(new Intent(this, OwnerDashboardActivity.class));
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
                                    String branch = documentSnapshot.getString("branch");

                                    currentUserName = name;
                                    currentUserEmail = email;
                                    currentUserBranch = branch;

                                    // Update UI with user data
                                    userNameText.setText(name + " (Employee)");
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error loading user data", e);
                                Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show();
                            });
                } else {
                    // Failed to load permissions
                    Log.e(TAG, "Failed to load permissions");
                    Toast.makeText(this, "Error checking permissions", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                }
            });
        }
    }

    private void loadInventoryItems() {
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading inventory items", error.toException());
            }
        });
    }

    private void showStockRequestDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_stock_request, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Initialize dialog views
        AutoCompleteTextView itemDropdown = dialogView.findViewById(R.id.itemDropdown);
        TextInputEditText quantityEditText = dialogView.findViewById(R.id.quantityEditText);
        TextInputEditText notesEditText = dialogView.findViewById(R.id.notesEditText);
        MaterialButton submitButton = dialogView.findViewById(R.id.submitButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Setup dropdown with inventory items
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, inventoryItems);
        itemDropdown.setAdapter(adapter);

        // Set item selection listener
        itemDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedItem = inventoryItems.get(position);
        });

        // Set click listeners
        submitButton.setOnClickListener(v -> {
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

            // Create and submit stock request
            submitStockRequest(selectedItem, quantity, notes, dialog);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void submitStockRequest(String item, int quantity, String notes, AlertDialog dialog) {
        showLoading(true);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            Toast.makeText(this, "You must be logged in to submit requests", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create stock request
        StockRequest request = new StockRequest(
                item,
                quantity,
                user.getUid(),
                currentUserName,
                currentUserEmail,
                currentUserBranch);

        if (!notes.isEmpty()) {
            request.setNotes(notes);
        }

        // Save to Firebase
        DatabaseReference requestsRef = FirebaseDatabase.getInstance().getReference("stockRequests");
        String requestId = requestsRef.push().getKey();

        if (requestId != null) {
            request.setId(requestId);
            requestsRef.child(requestId).setValue(request)
                    .addOnSuccessListener(aVoid -> {
                        showLoading(false);
                        dialog.dismiss();
                        Toast.makeText(this, "Stock request submitted successfully", Toast.LENGTH_SHORT).show();

                        // Send email notification to owner
                        EmailSender.findOwnerEmailAndSendRequest(this, request);
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Log.e(TAG, "Error saving stock request", e);
                        Toast.makeText(this, "Error submitting request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            showLoading(false);
            Toast.makeText(this, "Error generating request ID", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    // Sign out from Firebase
                    mAuth.signOut();

                    // Start login activity
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onShowItemDetail(InventoryItem item) {
        // Show item details dialog (read-only for employees)
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_item_details, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Initialize dialog views and set item details
        // (Using the same layout as in MainActivity)
        android.widget.ImageView itemImage = dialogView.findViewById(R.id.itemDetailsImage);
        TextView nameText = dialogView.findViewById(R.id.itemDetailsName);
        TextView categoryText = dialogView.findViewById(R.id.itemDetailsCategory);
        TextView quantityText = dialogView.findViewById(R.id.itemDetailsQuantity);
        TextView lastUpdatedText = dialogView.findViewById(R.id.itemDetailsLastUpdated);

        // Set item details
        nameText.setText(item.getName());
        categoryText.setText("Category: " + item.getCategory());
        quantityText.setText("Quantity: " + item.getQuantity());

        // Format last updated time
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String formattedDate = sdf.format(new Date(item.getLastUpdated()));
        lastUpdatedText.setText("Last updated: " + formattedDate);

        // Load item image
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(item.getImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .into(itemImage);
        }

        dialog.show();
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        requestStockButton.setEnabled(!show);
    }

    /**
     * Show stock request dialog for a specific inventory item
     * This method is called from InventoryFragment
     */
    public void showStockRequestDialog(InventoryItem item) {
        // Pre-select the item in the dialog
        selectedItem = item.getName();

        // Show the stock request dialog
        showStockRequestDialog();
    }

    // ViewPager adapter
    private static class EmployeePagerAdapter extends FragmentStateAdapter {
        public EmployeePagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new InventoryFragment();
                case 1:
                    return new AnnouncementsFragment();
                default:
                    return new InventoryFragment();
            }
        }

        @Override
        public int getItemCount() {
            return NUM_PAGES;
        }
    }
}
