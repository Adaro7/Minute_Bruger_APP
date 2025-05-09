package com.example.minuteburgers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.minuteburgers.adapters.InventoryAdapter;
import com.example.minuteburgers.utils.AppConfig;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryManagementActivity extends AppCompatActivity {
    private static final String TAG = "InventoryManagement";
    private static final int PICK_IMAGE_REQUEST = 1;

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private List<InventoryItem> inventoryItems = new ArrayList<>();
    private List<String> branches = new ArrayList<>();
    private List<String> categories = new ArrayList<>();

    private SearchView searchView;
    private AutoCompleteTextView branchFilterSpinner;
    private ExtendedFloatingActionButton addItemButton;
    private ImageButton backButton;
    private ProgressBar progressBar;
    private TextView emptyStateText;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private FirebaseFirestore firestore;
    private ValueEventListener inventoryListener;

    private String selectedBranch = "All Branches";
    private Uri selectedImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_management);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference("inventory");
        firestore = FirebaseFirestore.getInstance();

        // Initialize Cloudinary
        initializeCloudinary();

        // Initialize views
        initializeViews();

        // Load branches
        loadBranches();

        // Load categories
        loadCategories();

        // Setup RecyclerView
        setupRecyclerView();

        // Load inventory
        loadInventory();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.searchView);
        branchFilterSpinner = findViewById(R.id.branchFilterSpinner);
        addItemButton = findViewById(R.id.addItemButton);
        backButton = findViewById(R.id.backButton);
        progressBar = findViewById(R.id.progressBar);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup search view
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchInventory(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    loadInventory(); // Reset to show all items
                } else {
                    searchInventory(newText);
                }
                return true;
            }
        });

        // Setup branch filter
        branchFilterSpinner.setOnItemClickListener((parent, view, position, id) -> {
            selectedBranch = branches.get(position);
            filterInventoryByBranch(selectedBranch);
        });

        // Setup add item button
        addItemButton.setOnClickListener(v -> showAddItemDialog());

        // Setup back button
        backButton.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new InventoryAdapter(inventoryItems);

        adapter.setOnItemClickListener(new InventoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(InventoryItem item) {
                showItemDetails(item);
            }

            @Override
            public void onEditClick(InventoryItem item) {
                showEditItemDialog(item);
            }

            @Override
            public void onDeleteClick(InventoryItem item) {
                showDeleteConfirmationDialog(item);
            }
        });

        recyclerView.setAdapter(adapter);
    }

    private void loadBranches() {
        // Add default "All Branches" option
        branches.clear();
        branches.add("All Branches");

        // Load branches from Firestore
        firestore.collection("users")
                .whereEqualTo("role", "employee")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String branch = document.getString("branch");
                        if (branch != null && !branch.isEmpty() && !branches.contains(branch)) {
                            branches.add(branch);
                        }
                    }

                    // Update branch spinner
                    ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(
                            this, android.R.layout.simple_dropdown_item_1line, branches);
                    branchFilterSpinner.setAdapter(branchAdapter);
                    branchFilterSpinner.setText(selectedBranch, false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading branches", e);
                    Toast.makeText(this, "Error loading branches", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadCategories() {
        // Add default categories
        categories.clear();
        categories.add("Burger");
        categories.add("Drink");
        categories.add("Side");
        categories.add("Dessert");
        categories.add("Other");
    }

    private void loadInventory() {
        progressBar.setVisibility(View.VISIBLE);
        emptyStateText.setVisibility(View.GONE);

        // Remove previous listener if exists
        if (inventoryListener != null) {
            dbRef.removeEventListener(inventoryListener);
        }

        inventoryListener = dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                inventoryItems.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    InventoryItem item = itemSnapshot.getValue(InventoryItem.class);
                    if (item != null) {
                        item.setId(itemSnapshot.getKey());
                        inventoryItems.add(item);
                    }
                }

                // Apply branch filter if selected
                if (selectedBranch != null && !selectedBranch.equals("All Branches")) {
                    filterInventoryByBranch(selectedBranch);
                } else {
                    adapter.updateItems(inventoryItems);
                }

                // Show empty state if no items
                if (inventoryItems.isEmpty()) {
                    emptyStateText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyStateText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }

                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading inventory", error.toException());
                Toast.makeText(InventoryManagementActivity.this,
                        "Error loading inventory: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void searchInventory(String query) {
        if (query == null || query.isEmpty()) {
            loadInventory();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Search by name
        Query searchQuery = dbRef.orderByChild("name")
                .startAt(query)
                .endAt(query + "\uf8ff");

        searchQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<InventoryItem> searchResults = new ArrayList<>();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    InventoryItem item = itemSnapshot.getValue(InventoryItem.class);
                    if (item != null) {
                        item.setId(itemSnapshot.getKey());

                        // Apply branch filter if selected
                        if (selectedBranch.equals("All Branches") ||
                            selectedBranch.equals(item.getBranch())) {
                            searchResults.add(item);
                        }
                    }
                }

                adapter.updateItems(searchResults);

                // Show empty state if no results
                if (searchResults.isEmpty()) {
                    emptyStateText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyStateText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }

                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error searching inventory", error.toException());
                Toast.makeText(InventoryManagementActivity.this,
                        "Error searching inventory: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void filterInventoryByBranch(String branch) {
        if (branch == null || branch.equals("All Branches")) {
            adapter.updateItems(inventoryItems);
            return;
        }

        List<InventoryItem> filteredItems = new ArrayList<>();

        for (InventoryItem item : inventoryItems) {
            if (branch.equals(item.getBranch()) || "All Branches".equals(item.getBranch())) {
                filteredItems.add(item);
            }
        }

        adapter.updateItems(filteredItems);

        // Show empty state if no results
        if (filteredItems.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showAddItemDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_item_with_branch, null);
        builder.setView(dialogView);

        // Initialize dialog views
        ImageView itemImagePreview = dialogView.findViewById(R.id.itemImagePreview);
        Button selectImageButton = dialogView.findViewById(R.id.selectImageButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);

        // Setup category spinner
        AutoCompleteTextView categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, categories);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setText(categories.get(0), false);

        // Setup branch spinner
        AutoCompleteTextView branchSpinner = dialogView.findViewById(R.id.branchSpinner);
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, branches);
        branchSpinner.setAdapter(branchAdapter);
        branchSpinner.setText(selectedBranch, false);

        // Create dialog
        AlertDialog dialog = builder.create();

        // Setup select image button
        selectImageButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST);
        });

        // Setup cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Setup save button
        saveButton.setOnClickListener(v -> {
            // Get input values
            String name = ((TextView) dialogView.findViewById(R.id.itemNameInput)).getText().toString().trim();
            String quantityStr = ((TextView) dialogView.findViewById(R.id.itemQuantityInput)).getText().toString().trim();
            String category = categorySpinner.getText().toString();
            String branch = branchSpinner.getText().toString();

            // Validate input
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (quantityStr.isEmpty()) {
                Toast.makeText(this, "Please enter quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            int quantity = Integer.parseInt(quantityStr);

            // Create new item
            InventoryItem newItem = new InventoryItem(name, quantity, category, branch);

            // Set current user as last updated by
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                newItem.setLastUpdatedBy(currentUser.getEmail());
            }

            // Set last updated timestamp
            newItem.setLastUpdated(System.currentTimeMillis());

            // If image selected, upload to Cloudinary
            if (selectedImageUri != null) {
                uploadImageAndSaveItem(selectedImageUri, newItem, dialog);
            } else {
                // Save item without image
                saveItemToDatabase(newItem, dialog);
            }
        });

        dialog.show();
    }

    private void showEditItemDialog(InventoryItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_item_with_branch, null);
        builder.setView(dialogView);

        // Initialize dialog views
        ImageView itemImagePreview = dialogView.findViewById(R.id.itemImagePreview);
        Button selectImageButton = dialogView.findViewById(R.id.selectImageButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);

        // Set existing values
        ((TextView) dialogView.findViewById(R.id.itemNameInput)).setText(item.getName());
        ((TextView) dialogView.findViewById(R.id.itemQuantityInput)).setText(String.valueOf(item.getQuantity()));

        // Load existing image if available
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(item.getImageUrl())
                    .into(itemImagePreview);
        }

        // Setup category spinner
        AutoCompleteTextView categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, categories);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setText(item.getCategory() != null ? item.getCategory() : categories.get(0), false);

        // Setup branch spinner
        AutoCompleteTextView branchSpinner = dialogView.findViewById(R.id.branchSpinner);
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, branches);
        branchSpinner.setAdapter(branchAdapter);
        branchSpinner.setText(item.getBranch() != null ? item.getBranch() : "All Branches", false);

        // Create dialog
        AlertDialog dialog = builder.create();

        // Setup select image button
        selectImageButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST);
        });

        // Setup cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Setup save button
        saveButton.setOnClickListener(v -> {
            // Get input values
            String name = ((TextView) dialogView.findViewById(R.id.itemNameInput)).getText().toString().trim();
            String quantityStr = ((TextView) dialogView.findViewById(R.id.itemQuantityInput)).getText().toString().trim();
            String category = categorySpinner.getText().toString();
            String branch = branchSpinner.getText().toString();

            // Validate input
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (quantityStr.isEmpty()) {
                Toast.makeText(this, "Please enter quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            int quantity = Integer.parseInt(quantityStr);

            // Update item
            item.setName(name);
            item.setQuantity(quantity);
            item.setCategory(category);
            item.setBranch(branch);

            // Set current user as last updated by
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                item.setLastUpdatedBy(currentUser.getEmail());
            }

            // Set last updated timestamp
            item.setLastUpdated(System.currentTimeMillis());

            // If image selected, upload to Cloudinary
            if (selectedImageUri != null) {
                uploadImageAndSaveItem(selectedImageUri, item, dialog);
            } else {
                // Save item without changing image
                updateItemInDatabase(item, dialog);
            }
        });

        dialog.show();
    }

    private void showDeleteConfirmationDialog(InventoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete " + item.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteItem(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showItemDetails(InventoryItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_item_details, null);

        // If dialog_item_details.xml doesn't exist, create a simple dialog
        if (dialogView == null) {
            builder.setTitle(item.getName())
                    .setMessage("Quantity: " + item.getQuantity() + "\n" +
                            "Category: " + item.getCategory() + "\n" +
                            "Branch: " + item.getBranch() + "\n" +
                            "Last Updated: " + new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm")
                                    .format(new java.util.Date(item.getLastUpdated())) + "\n" +
                            "Updated By: " + item.getLastUpdatedBy())
                    .setPositiveButton("Close", null);
        } else {
            // Setup dialog views if layout exists
            builder.setView(dialogView);

            // Set item details
            // (Implement this if you create the dialog_item_details.xml layout)
        }

        builder.create().show();
    }

    private void uploadImageAndSaveItem(Uri imageUri, InventoryItem item, AlertDialog dialog) {
        progressBar.setVisibility(View.VISIBLE);

        // Generate a unique filename with timestamp to avoid conflicts
        String filename = "inventory_" + System.currentTimeMillis();

        try {
            // Initialize Cloudinary with proper credentials
            initializeCloudinary();

            // Show upload started toast
            Toast.makeText(this, "Uploading image to Cloudinary...", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Starting Cloudinary upload for image: " + imageUri);

            // Upload image to Cloudinary with improved options
            MediaManager.get().upload(imageUri)
                    .option("public_id", filename)
                    .option("folder", "minute_burgers/inventory")
                    .option("resource_type", "image")
                    .option("tags", "inventory,minute_burgers")
                    .option("overwrite", true)
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Cloudinary upload started with request ID: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            // Calculate progress percentage
                            double progress = (100.0 * bytes) / totalBytes;
                            Log.d(TAG, "Cloudinary upload is " + progress + "% done");

                            // Update progress bar if needed
                            runOnUiThread(() -> {
                                // You could update a specific progress indicator here
                                if (progress > 95) {
                                    Toast.makeText(InventoryManagementActivity.this,
                                        "Almost done...",
                                        Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            // Log the full result for debugging
                            Log.d(TAG, "Cloudinary upload successful. Result: " + resultData.toString());

                            // Get the secure URL from the result
                            String imageUrl = resultData.get("secure_url").toString();
                            Log.d(TAG, "Image URL: " + imageUrl);

                            // Set the image URL on the item
                            item.setImageUrl(imageUrl);

                            // Save or update item in database
                            if (item.getId() == null) {
                                saveItemToDatabase(item, dialog);
                            } else {
                                updateItemInDatabase(item, dialog);
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Cloudinary upload error: " + error.getDescription());

                            // Show detailed error message
                            runOnUiThread(() -> {
                                Toast.makeText(InventoryManagementActivity.this,
                                        "Error uploading image: " + error.getDescription(),
                                        Toast.LENGTH_LONG).show();
                                progressBar.setVisibility(View.GONE);

                                // Show a dialog with retry option for serious errors
                                if (error.getDescription().contains("API key") ||
                                    error.getDescription().contains("authentication")) {
                                    new AlertDialog.Builder(InventoryManagementActivity.this)
                                        .setTitle("Authentication Error")
                                        .setMessage("There was a problem with the image upload service authentication. Please contact support.")
                                        .setPositiveButton("OK", null)
                                        .show();
                                }
                            });
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Cloudinary upload rescheduled: " + error.getDescription());
                            // This is usually a temporary issue, so we can just log it
                        }
                    })
                    .dispatch();
        } catch (Exception e) {
            // Handle any unexpected errors
            Log.e(TAG, "Error in Cloudinary upload process", e);
            Toast.makeText(this, "Error uploading image: " + e.getMessage(), Toast.LENGTH_LONG).show();
            progressBar.setVisibility(View.GONE);

            // If it's a configuration error, show more helpful message
            if (e.getMessage() != null &&
                (e.getMessage().contains("not initialized") ||
                 e.getMessage().contains("configuration"))) {
                Toast.makeText(this,
                    "Image upload service not properly configured. Please try again later.",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Initialize Cloudinary with proper configuration from AppConfig
     */
    private void initializeCloudinary() {
        try {
            // Check if MediaManager is already initialized
            boolean isInitialized = false;
            try {
                // Try to get the instance - if it works, it's initialized
                MediaManager.get();
                isInitialized = true;
                Log.d(TAG, "Cloudinary already initialized");
            } catch (IllegalStateException e) {
                // Not initialized yet
                isInitialized = false;
            }

            // Only initialize if not already initialized
            if (!isInitialized) {
                // Get configuration from AppConfig
                Map<String, String> config = AppConfig.getCloudinaryConfig();

                // Initialize MediaManager with configuration
                MediaManager.init(this, config);
                Log.d(TAG, "Cloudinary initialized successfully with cloud name: " + AppConfig.CLOUDINARY_CLOUD_NAME);
            }

            // Verify initialization by getting the instance
            MediaManager manager = MediaManager.get();
            if (manager != null) {
                Log.d(TAG, "Cloudinary MediaManager instance verified");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Cloudinary", e);
            Toast.makeText(this, "Error initializing image upload service: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveItemToDatabase(InventoryItem item, AlertDialog dialog) {
        // Generate a new key for the item
        String key = dbRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Error creating item: Could not generate key", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        // Set the ID
        item.setId(key);

        // Save to database
        dbRef.child(key).setValue(item)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(InventoryManagementActivity.this,
                            "Item added successfully", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    progressBar.setVisibility(View.GONE);
                    selectedImageUri = null; // Reset selected image
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding item", e);
                    Toast.makeText(InventoryManagementActivity.this,
                            "Error adding item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
    }

    private void updateItemInDatabase(InventoryItem item, AlertDialog dialog) {
        // Update in database
        dbRef.child(item.getId()).setValue(item)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(InventoryManagementActivity.this,
                            "Item updated successfully", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    progressBar.setVisibility(View.GONE);
                    selectedImageUri = null; // Reset selected image
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating item", e);
                    Toast.makeText(InventoryManagementActivity.this,
                            "Error updating item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
    }

    private void deleteItem(InventoryItem item) {
        progressBar.setVisibility(View.VISIBLE);

        // Delete from database
        dbRef.child(item.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(InventoryManagementActivity.this,
                            "Item deleted successfully", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting item", e);
                    Toast.makeText(InventoryManagementActivity.this,
                            "Error deleting item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();

            // Find all dialogs currently showing
            AlertDialog currentDialog = null;

            // Since we can't directly access the current dialog, we'll use a simpler approach
            // The dialog should be the last one created, so we'll just update the image in all dialogs

            // Get all dialogs from the window manager
            android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
            if (params != null && params.token != null) {
                // Find all views with the itemImagePreview ID in the current window
                View decorView = getWindow().getDecorView();
                if (decorView != null) {
                    ImageView imagePreview = decorView.findViewById(R.id.itemImagePreview);
                    if (imagePreview != null) {
                        Glide.with(this).load(selectedImageUri).into(imagePreview);
                    }
                }
            }

            // Show a toast to confirm image selection
            Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove database listener
        if (inventoryListener != null) {
            dbRef.removeEventListener(inventoryListener);
        }
    }
}
