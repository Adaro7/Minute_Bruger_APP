package com.example.minuteburgers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.Cloudinary;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.cloudinary.utils.ObjectUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bumptech.glide.Glide;
import com.google.android.material.textview.MaterialTextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;
    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private Uri selectedImageUri;
    private ImageView imagePreview;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private SearchView searchView;
    private MaterialButton dashboardButton, reportsButton, staffButton;
    private FloatingActionButton addItemButton;
    private ImageButton logoutButton;
    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private ValueEventListener inventoryListener;
    private TextView userNameText;
    private NotificationHelper notificationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Enable Firebase persistence first, before any other Firebase calls
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            } catch (Exception e) {
                Log.d(TAG, "Firebase persistence already enabled");
            }

            // Initialize Firebase
            FirebaseApp.initializeApp(this);
            mAuth = FirebaseAuth.getInstance();
            dbRef = FirebaseDatabase.getInstance().getReference("inventory");

            // Enable disk persistence for this reference
            dbRef.keepSynced(true);

            // Set database to maintain sync even when offline
            FirebaseDatabase.getInstance().getReference().keepSynced(true);

            // Initialize Cloudinary
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", "djf2atu0h");
            config.put("api_key", "216549336527232");
            config.put("api_secret", "VxyHB2o9pzjfNpXQzWNFYfsSJ6I");
            config.put("secure", "true");  // Force HTTPS
            config.put("cloudinary_url", "cloudinary://216549336527232:VxyHB2o9pzjfNpXQzWNFYfsSJ6I@djf2atu0h");

            try {
                MediaManager.init(this, config);
            } catch (IllegalStateException e) {
                // If already initialized, just log it
                Log.d(TAG, "Cloudinary already initialized");
            }

            // Initialize image picker
            imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        if (imagePreview != null) {
                            imagePreview.setImageURI(uri);
                        }
                    }
                }
            );

            // Check if user is logged in
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }

            // Initialize views and setup
            initializeViews();
            setupRecyclerView();
            setupClickListeners();
            setupSearchView();
            loadUserName();

            // Start listening for inventory changes
            attachInventoryListener();

            // Check if database is empty and initialize if needed
            checkAndInitializeDatabase();

            Log.d(TAG, "MainActivity initialized successfully");

            // Request notification permission for Android 13 and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_PERMISSION_CODE);
                }
            }

            // Get FCM token
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();
                        Log.d(TAG, "FCM Token: " + token);
                    });

            // Subscribe to a topic (optional)
            FirebaseMessaging.getInstance().subscribeToTopic("all")
                    .addOnCompleteListener(task -> {
                        String msg = "Subscribed to notifications";
                        if (!task.isSuccessful()) {
                            msg = "Subscribe failed";
                        }
                        Log.d(TAG, msg);
                    });

            // Initialize NotificationHelper
            notificationHelper = new NotificationHelper(this);
            
            // Subscribe to relevant topics
            notificationHelper.subscribeToTopic("all");
            notificationHelper.subscribeToTopic("inventory_alerts");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing app: ", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inventoryListener != null && dbRef != null) {
            dbRef.removeEventListener(inventoryListener);
        }
    }

    private void initializeViews() {
        try {
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            searchView = findViewById(R.id.searchView);
            dashboardButton = findViewById(R.id.dashboardButton);
            reportsButton = findViewById(R.id.reportsButton);
            staffButton = findViewById(R.id.staffButton);
            addItemButton = findViewById(R.id.addItemButton);
            userNameText = findViewById(R.id.userNameText);
            recyclerView = findViewById(R.id.recyclerView);
            imagePreview = findViewById(R.id.itemImagePreview);

            // Set click listener for username
            userNameText.setOnClickListener(v -> handleLogout());

            Log.d(TAG, "Views initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: ", e);
            throw e;
        }
    }

    private void setupRecyclerView() {
        try {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
            adapter = new InventoryAdapter(new ArrayList<>());

            adapter.setOnItemClickListener(new InventoryAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(InventoryItem item) {
                    showItemDetails(item);
                }

                @Override
                public void onEditClick(InventoryItem item) {
                    showEditDialog(item);
                }

                @Override
                public void onDeleteClick(InventoryItem item) {
                    deleteItem(item);
                }
            });

            recyclerView.setAdapter(adapter);
            Log.d(TAG, "RecyclerView setup complete");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerVie1w: ", e);
            throw e;
        }
    }

    private void setupClickListeners() {
        try {
            dashboardButton.setOnClickListener(v -> loadDashboard());
            reportsButton.setOnClickListener(v -> handleReports());
            staffButton.setOnClickListener(v -> handleStaffList());
            addItemButton.setOnClickListener(v -> showAddDialog());
            logoutButton.setOnClickListener(v -> handleLogout());
            Log.d(TAG, "Click listeners setup complete");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners: ", e);
            Toast.makeText(this, "Error setting up buttons", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchInventory(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchInventory(newText);
                return true;
            }
        });
    }

    private void attachInventoryListener() {
        inventoryListener = dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<InventoryItem> items = new ArrayList<>();
                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    InventoryItem item = itemSnapshot.getValue(InventoryItem.class);
                    if (item != null) {
                        item.setId(itemSnapshot.getKey());
                        items.add(item);
                    }
                }
                adapter.updateItems(items);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading inventory: ", error.toException());
                Toast.makeText(MainActivity.this, "Error loading inventory", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchInventory(String query) {
        if (query.isEmpty()) {
            attachInventoryListener();
            return;
        }

        Query searchQuery = dbRef.orderByChild("name")
                .startAt(query)
                .endAt(query + "\uf8ff");

        searchQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<InventoryItem> items = new ArrayList<>();
                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    InventoryItem item = itemSnapshot.getValue(InventoryItem.class);
                    if (item != null) {
                        item.setId(itemSnapshot.getKey());
                        items.add(item);
                    }
                }
                adapter.updateItems(items);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error searching inventory: ", error.toException());
                Toast.makeText(MainActivity.this, "Error searching inventory", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDashboard() {
        try {
            // Show inventory grid view (current view)
            Toast.makeText(this, "Dashboard View", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error loading dashboard: ", e);
        }
    }

    private void handleReports() {
        try {
            Intent intent = new Intent(this, ReportsActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening reports: ", e);
            Toast.makeText(this, "Error opening reports", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleStaffList() {
        try {
            Intent intent = new Intent(this, StaffListActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening staff list: ", e);
            Toast.makeText(this, "Error opening staff list", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLogout() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    // Clear Firebase cache
                    FirebaseDatabase.getInstance().purgeOutstandingWrites();

                    // Clear Cloudinary cache
                    MediaManager.get().cancelAllRequests();

                    // Clear Glide image cache
                    new Thread(() -> {
                        try {
                            Glide.get(MainActivity.this).clearDiskCache();
                        } catch (Exception e) {
                            Log.e(TAG, "Error clearing Glide cache: " + e.getMessage());
                        }
                    }).start();
                    Glide.get(this).clearMemory();

                    // Sign out from Firebase
                    mAuth.signOut();

                    // Clear any local references
                    if (adapter != null) {
                        adapter.updateItems(new ArrayList<>());
                    }

                    // Start login activity
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error during logout: ", e);
            Toast.makeText(this, "Error logging out", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_item, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("Add New Item");

        AlertDialog dialog = builder.create();

        // Initialize views
        TextInputEditText nameInput = dialogView.findViewById(R.id.itemNameInput);
        TextInputEditText quantityInput = dialogView.findViewById(R.id.itemQuantityInput);
        AutoCompleteTextView categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        Button selectImageButton = dialogView.findViewById(R.id.selectImageButton);
        imagePreview = dialogView.findViewById(R.id.itemImagePreview);
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Set up category spinner
        String[] categories = {"Burgers", "Drinks", "Sides", "Desserts"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, categories
        );
        categorySpinner.setAdapter(adapter);

        // Set up image selection
        selectImageButton.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String quantityStr = quantityInput.getText().toString().trim();
            String category = categorySpinner.getText().toString().trim();

            if (name.isEmpty() || quantityStr.isEmpty() || category.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int quantity = Integer.parseInt(quantityStr);

                if (selectedImageUri == null) {
                    Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Upload image to Cloudinary
                MediaManager.get()
                    .upload(selectedImageUri)
                    .option("folder", "public")
                    .option("resource_type", "image")
                    .option("use_filename", true)
                    .option("unique_filename", true)
                    .option("secure", true)
                    .option("transformation", "w_auto,c_scale")  // Auto-optimize image size
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Toast.makeText(MainActivity.this, "Uploading image...", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Starting image upload to Cloudinary");
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            int progress = (int) ((bytes * 100) / totalBytes);
                            Log.d(TAG, "Upload progress: " + progress + "%");
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            String imageUrl = (String) resultData.get("secure_url");
                            Log.d(TAG, "Upload success. Secure URL: " + imageUrl);

                            if (imageUrl == null || imageUrl.isEmpty()) {
                                Log.e(TAG, "Error: Received null or empty URL from Cloudinary");
                                Toast.makeText(MainActivity.this, "Error: Invalid image URL received", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Create new item with Cloudinary image URL
                            InventoryItem newItem = new InventoryItem();
                            newItem.setName(name);
                            newItem.setQuantity(quantity);
                            newItem.setCategory(category);
                            newItem.setImageUrl(imageUrl);
                            newItem.setLastUpdated(System.currentTimeMillis());

                            // Log the item details before saving
                            Log.d(TAG, "Saving item with image URL: " + imageUrl);

                            addItemToDatabase(newItem);
                            dialog.dismiss();
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Error uploading image: " + error.getDescription());
                            Toast.makeText(MainActivity.this, "Error uploading image: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditDialog(InventoryItem item) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_item, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("Edit Item");

        AlertDialog dialog = builder.create();

        TextInputEditText nameInput = dialogView.findViewById(R.id.itemNameInput);
        TextInputEditText quantityInput = dialogView.findViewById(R.id.itemQuantityInput);
        AutoCompleteTextView categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        Button selectImageButton = dialogView.findViewById(R.id.selectImageButton);
        imagePreview = dialogView.findViewById(R.id.itemImagePreview);
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Pre-fill the fields
        nameInput.setText(item.getName());
        quantityInput.setText(String.valueOf(item.getQuantity()));
        categorySpinner.setText(item.getCategory());

        // Load existing image
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(this)
                .load(item.getImageUrl())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .into(imagePreview);
        }

        // Set up category spinner
        String[] categories = {"Burgers", "Drinks", "Sides", "Desserts"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, categories
        );
        categorySpinner.setAdapter(spinnerAdapter);

        // Set up image selection
        selectImageButton.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String quantityStr = quantityInput.getText().toString().trim();
            String category = categorySpinner.getText().toString().trim();

            if (name.isEmpty() || quantityStr.isEmpty() || category.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int quantity = Integer.parseInt(quantityStr);

                if (selectedImageUri != null) {
                    // Upload new image to Cloudinary
                    MediaManager.get()
                        .upload(selectedImageUri)
                        .option("folder", "Minuteburger")
                        .option("resource_type", "image")
                        .option("use_filename", true)
                        .option("unique_filename", true)
                        .option("secure", true)
                        .option("transformation", "w_auto,c_scale")
                        .callback(new UploadCallback() {
                            @Override
                            public void onStart(String requestId) {
                                Toast.makeText(MainActivity.this, "Uploading image...", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onProgress(String requestId, long bytes, long totalBytes) {
                                int progress = (int) ((bytes * 100) / totalBytes);
                                Log.d(TAG, "Upload progress: " + progress + "%");
                            }

                            @Override
                            public void onSuccess(String requestId, Map resultData) {
                                String imageUrl = (String) resultData.get("secure_url");
                                if (imageUrl != null && !imageUrl.isEmpty()) {
                                    InventoryItem updatedItem = new InventoryItem(name, quantity, category);
                                    updatedItem.setId(item.getId());
                                    updatedItem.setImageUrl(imageUrl);
                                    updatedItem.setLastUpdated(System.currentTimeMillis());
                                    updateItemInDatabase(updatedItem);
                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(MainActivity.this, "Error: Invalid image URL received", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onError(String requestId, ErrorInfo error) {
                                Toast.makeText(MainActivity.this, "Error uploading image: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onReschedule(String requestId, ErrorInfo error) {
                                Log.e(TAG, "Upload rescheduled: " + error.getDescription());
                            }
                        })
                        .dispatch();
                } else {
                    // No new image selected, update with existing image
                    InventoryItem updatedItem = new InventoryItem(name, quantity, category);
                    updatedItem.setId(item.getId());
                    updatedItem.setImageUrl(item.getImageUrl());
                    updatedItem.setLastUpdated(System.currentTimeMillis());
                    updateItemInDatabase(updatedItem);
                    dialog.dismiss();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showItemDetails(InventoryItem item) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_item_details, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        ImageView itemImage = dialogView.findViewById(R.id.itemDetailsImage);
        MaterialTextView nameText = dialogView.findViewById(R.id.itemDetailsName);
        MaterialTextView categoryText = dialogView.findViewById(R.id.itemDetailsCategory);
        MaterialTextView quantityText = dialogView.findViewById(R.id.itemDetailsQuantity);
        MaterialTextView lastUpdatedText = dialogView.findViewById(R.id.itemDetailsLastUpdated);
        RecyclerView historyRecyclerView = dialogView.findViewById(R.id.historyRecyclerView);

        // Set up history RecyclerView
        HistoryAdapter historyAdapter = new HistoryAdapter();
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);

        // Load item image
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(this)
                .load(item.getImageUrl())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .into(itemImage);
        }

        // Set item details
        nameText.setText(item.getName());
        categoryText.setText("Category: " + item.getCategory());
        quantityText.setText("Quantity: " + item.getQuantity());

        // Format last updated time
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String formattedDate = sdf.format(new Date(item.getLastUpdated()));
        String lastUpdatedBy = item.getLastUpdatedBy() != null ? " by " + item.getLastUpdatedBy() : "";
        lastUpdatedText.setText("Last updated: " + formattedDate + lastUpdatedBy);

        // Update history
        if (item.getHistory() != null) {
            historyAdapter.updateHistory(item.getHistory());
        }

        dialog.show();
    }

    private void addItemToDatabase(InventoryItem item) {
        String key = dbRef.push().getKey();
        if (key != null) {
            item.setId(key);
            item.setLastUpdatedBy(mAuth.getCurrentUser().getEmail());
            item.addHistoryEntry("Created", mAuth.getCurrentUser().getEmail(), 
                "Item created with quantity: " + item.getQuantity());
            
            dbRef.child(key).setValue(item)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item added successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding item: ", e);
                    Toast.makeText(this, "Error adding item", Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void updateItemInDatabase(InventoryItem item) {
        if (item.getId() != null) {
            item.setLastUpdatedBy(mAuth.getCurrentUser().getEmail());
            item.addHistoryEntry("Updated", mAuth.getCurrentUser().getEmail(), 
                "Item updated with new quantity: " + item.getQuantity());
            
            // Check if inventory is low (less than 10 items)
            if (item.getQuantity() < 10) {
                notificationHelper.sendLowInventoryNotification(item.getName(), item.getQuantity());
            }
            
            dbRef.child(item.getId()).setValue(item)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating item: ", e);
                    Toast.makeText(this, "Error updating item", Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void deleteItem(InventoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete " + item.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteItemFromDatabase(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteItemFromDatabase(InventoryItem item) {
        if (item.getId() != null) {
            item.setLastUpdatedBy(mAuth.getCurrentUser().getEmail());
            item.addHistoryEntry("Deleted", mAuth.getCurrentUser().getEmail(), 
                "Item deleted from inventory");
            
            dbRef.child(item.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting item: ", e);
                    Toast.makeText(this, "Error deleting item", Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void checkAndInitializeDatabase() {
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Database is empty, add a test item
                    InventoryItem testItem = new InventoryItem();
                    testItem.setName("Test Burger");
                    testItem.setQuantity(10);
                    testItem.setCategory("Burgers");
                    testItem.setLastUpdated(System.currentTimeMillis());

                    String key = dbRef.push().getKey();
                    if (key != null) {
                        testItem.setId(key);
                        dbRef.child(key).setValue(testItem)
                            .addOnSuccessListener(aVoid ->
                                Log.d(TAG, "Test item added successfully"))
                            .addOnFailureListener(e ->
                                Log.e(TAG, "Error adding test item: ", e));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking database: ", error.toException());
            }
        });
    }

    private void loadUserName() {
        String userId = mAuth.getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String name = documentSnapshot.getString("name");
                    if (name != null && !name.isEmpty()) {
                        userNameText.setText(name);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user name: ", e);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}