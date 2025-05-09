package com.example.minuteburgers;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.InventoryItem;
import com.example.minuteburgers.adapters.EmployeeAdapter;
import com.example.minuteburgers.adapters.StockRequestAdapter;
import com.example.minuteburgers.models.StockRequest;
import com.example.minuteburgers.models.User;
import com.example.minuteburgers.utils.NotificationHelper;
import com.example.minuteburgers.utils.PermissionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class OwnerDashboardActivity extends AppCompatActivity {
    private static final String TAG = "OwnerDashboardActivity";

    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private LinearProgressIndicator progressIndicator;
    private MaterialButton backButton, manageEmployeesButton, announcementsButton, editProfileButton, inventoryManagementButton, logoutButton;
    private TextInputEditText searchInput;

    private EmployeeAdapter employeeAdapter;
    private StockRequestAdapter stockRequestAdapter;
    private List<User> employees = new ArrayList<>();
    private List<StockRequest> stockRequests = new ArrayList<>();

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DatabaseReference stockRequestsRef;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_dashboard);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        stockRequestsRef = FirebaseDatabase.getInstance().getReference("stockRequests");

        // Initialize permission manager
        permissionManager = PermissionManager.getInstance();

        // Check if user has permission to access owner dashboard
        checkPermissions();

        // Initialize views
        initializeViews();
    }

    private void checkPermissions() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Load permissions
            permissionManager.loadUserPermissions(mAuth, db, success -> {
                if (success) {
                    // Check if user has permission to access owner dashboard
                    if (!permissionManager.hasPermission(PermissionManager.ACCESS_OWNER_DASHBOARD)) {
                        Toast.makeText(OwnerDashboardActivity.this,
                                "You don't have permission to access this dashboard",
                                Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "User role: " + permissionManager.getUserRole() +
                              " does not have permission to access owner dashboard");
                        finish();
                    } else {
                        // User has permission, load data
                        Log.d(TAG, "User has permission to access owner dashboard");
                        loadEmployees();
                        loadStockRequests();
                    }
                } else {
                    // Failed to load permissions
                    Log.e(TAG, "Failed to load permissions");
                    Toast.makeText(OwnerDashboardActivity.this,
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

    private void initializeViews() {
        tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        progressIndicator = findViewById(R.id.progressIndicator);
        backButton = findViewById(R.id.backButton);
        manageEmployeesButton = findViewById(R.id.manageEmployeesButton);
        announcementsButton = findViewById(R.id.announcementsButton);
        editProfileButton = findViewById(R.id.editProfileButton);
        inventoryManagementButton = findViewById(R.id.inventoryManagementButton);
        logoutButton = findViewById(R.id.logoutButton);
        searchInput = findViewById(R.id.searchInput);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapters
        employeeAdapter = new EmployeeAdapter(employees, new EmployeeAdapter.OnEmployeeActionListener() {
            @Override
            public void onEmployeeClick(User employee) {
                // Open employee details or edit screen
                Intent intent = new Intent(OwnerDashboardActivity.this, EmployeeManagementActivity.class);
                startActivity(intent);
            }

            @Override
            public void onEmployeeDelete(User employee) {
                // We don't allow deletion from the dashboard view
                Toast.makeText(OwnerDashboardActivity.this,
                    "Please go to Employee Management to delete employees",
                    Toast.LENGTH_SHORT).show();
            }
        });

        stockRequestAdapter = new StockRequestAdapter(stockRequests,
            new StockRequestAdapter.RequestActionListener() {
                @Override
                public void onRequestAction(StockRequest request, String action) {
                    handleRequestAction(request, action);
                }
            });

        // Set initial adapter
        recyclerView.setAdapter(employeeAdapter);

        // Setup tab selection listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // Employees tab
                    recyclerView.setAdapter(employeeAdapter);
                    searchInput.setHint("Search employees...");
                } else {
                    // Stock Requests tab
                    recyclerView.setAdapter(stockRequestAdapter);
                    searchInput.setHint("Search requests...");
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Setup search functionality
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterResults(s.toString());
            }
        });

        // Setup button click listeners
        backButton.setOnClickListener(v -> finish());

        manageEmployeesButton.setOnClickListener(v -> {
            // Open employee management activity
            startActivity(new android.content.Intent(this, EmployeeManagementActivity.class));
        });

        announcementsButton.setOnClickListener(v -> {
            // Open announcements activity
            startActivity(new android.content.Intent(this, AnnouncementsActivity.class));
        });

        // Set up sales report button
        findViewById(R.id.salesReportButton).setOnClickListener(v -> {
            try {
                // Open sales report activity with error handling
                Intent salesReportIntent = new Intent(this, SalesReportActivity.class);
                startActivity(salesReportIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error launching SalesReportActivity", e);
                Toast.makeText(this,
                    "Error opening Sales Report: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            }
        });

        editProfileButton.setOnClickListener(v -> {
            // Open profile edit activity
            startActivity(new android.content.Intent(this, EditProfileActivity.class));
        });

        inventoryManagementButton.setOnClickListener(v -> {
            try {
                // Open inventory management activity with error handling
                Intent inventoryIntent = new Intent(this, InventoryManagementActivity.class);
                startActivity(inventoryIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error launching InventoryManagementActivity", e);
                Toast.makeText(this,
                    "Error opening Inventory Management: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            }
        });

        logoutButton.setOnClickListener(v -> {
            // Show logout confirmation dialog
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    // Logout user
                    mAuth.signOut();
                    // Return to login screen
                    startActivity(new android.content.Intent(this, LoginActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void loadEmployees() {
        showLoading(true);
        db.collection("users")
                .whereEqualTo("role", "employee")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    employees.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String uid = document.getId();
                        String name = document.getString("name");
                        String email = document.getString("email");
                        String branch = document.getString("branch");

                        User employee = new User(uid, name, email, "employee", branch);
                        employees.add(employee);
                    }

                    employeeAdapter.notifyDataSetChanged();
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading employees", e);
                    Toast.makeText(OwnerDashboardActivity.this,
                            "Error loading employees",
                            Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
    }

    private void loadStockRequests() {
        showLoading(true);
        stockRequestsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stockRequests.clear();
                for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                    StockRequest request = requestSnapshot.getValue(StockRequest.class);
                    if (request != null) {
                        request.setId(requestSnapshot.getKey());
                        stockRequests.add(request);
                    }
                }

                stockRequestAdapter.notifyDataSetChanged();
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading stock requests", error.toException());
                Toast.makeText(OwnerDashboardActivity.this,
                        "Error loading stock requests",
                        Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void handleRequestAction(StockRequest request, String action) {
        if (request == null || request.getId() == null) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation dialog before proceeding
        new AlertDialog.Builder(this)
            .setTitle(action.equals("approved") ? "Approve Request" : "Reject Request")
            .setMessage("Are you sure you want to " + action + " this request for " +
                    request.getQuantity() + " " + request.getItemName() + "?")
            .setPositiveButton("Yes", (dialog, which) -> {
                processRequestAction(request, action);
            })
            .setNegativeButton("No", null)
            .show();
    }

    private void processRequestAction(StockRequest request, String action) {
        showLoading(true);

        // Update request status
        request.setStatus(action);
        request.setResponseDate(new java.util.Date());
        request.setRespondedBy(mAuth.getCurrentUser().getUid());

        stockRequestsRef.child(request.getId()).setValue(request)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(OwnerDashboardActivity.this,
                            "Request " + action,
                            Toast.LENGTH_SHORT).show();

                    // If approved, update inventory quantity
                    if (action.equals("approved")) {
                        updateInventoryForApprovedRequest(request);
                    }

                    // Send notification to employee
                    NotificationHelper notificationHelper = new NotificationHelper(OwnerDashboardActivity.this);
                    notificationHelper.sendStockRequestStatusNotification(request);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error updating request", e);
                    Toast.makeText(OwnerDashboardActivity.this,
                            "Error updating request",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void filterResults(String query) {
        if (tabLayout.getSelectedTabPosition() == 0) {
            // Filter employees
            filterEmployees(query);
        } else {
            // Filter stock requests
            filterStockRequests(query);
        }
    }

    private void filterEmployees(String query) {
        query = query.toLowerCase().trim();

        if (query.isEmpty()) {
            // If query is empty, reload all employees
            loadEmployees();
            return;
        }

        // Filter employees based on name, email, or branch
        List<User> filteredList = new ArrayList<>();
        for (User employee : employees) {
            if (employee.getName().toLowerCase().contains(query) ||
                    employee.getEmail().toLowerCase().contains(query) ||
                    employee.getBranch().toLowerCase().contains(query)) {
                filteredList.add(employee);
            }
        }

        // Update adapter with filtered list
        employeeAdapter = new EmployeeAdapter(filteredList, new EmployeeAdapter.OnEmployeeActionListener() {
            @Override
            public void onEmployeeClick(User employee) {
                // Open employee details or edit screen
                Intent intent = new Intent(OwnerDashboardActivity.this, EmployeeManagementActivity.class);
                startActivity(intent);
            }

            @Override
            public void onEmployeeDelete(User employee) {
                // We don't allow deletion from the dashboard view
                Toast.makeText(OwnerDashboardActivity.this,
                    "Please go to Employee Management to delete employees",
                    Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setAdapter(employeeAdapter);

        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No employees found matching '" + query + "'", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterStockRequests(String query) {
        query = query.toLowerCase().trim();

        if (query.isEmpty()) {
            // If query is empty, reload all stock requests
            loadStockRequests();
            return;
        }

        // Filter stock requests based on item name, requester, or status
        List<StockRequest> filteredList = new ArrayList<>();
        for (StockRequest request : stockRequests) {
            if (request.getItemName().toLowerCase().contains(query) ||
                    request.getRequestedByName().toLowerCase().contains(query) ||
                    request.getStatus().toLowerCase().contains(query)) {
                filteredList.add(request);
            }
        }

        // Update adapter with filtered list
        stockRequestAdapter = new StockRequestAdapter(filteredList,
            new StockRequestAdapter.RequestActionListener() {
                @Override
                public void onRequestAction(StockRequest request, String action) {
                    handleRequestAction(request, action);
                }
            });
        recyclerView.setAdapter(stockRequestAdapter);

        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No requests found matching '" + query + "'", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update inventory when a stock request is approved
     */
    private void updateInventoryForApprovedRequest(StockRequest request) {
        // Log the request details for debugging
        Log.d(TAG, "Updating inventory for approved request: " +
              "Item: " + request.getItemName() +
              ", Quantity: " + request.getQuantity() +
              ", Branch: " + request.getBranch());

        // Get reference to inventory database
        DatabaseReference inventoryRef = FirebaseDatabase.getInstance().getReference("inventory");

        // Query for the item by name
        inventoryRef.orderByChild("name").equalTo(request.getItemName())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Log.d(TAG, "Found matching inventory items: " + snapshot.getChildrenCount());

                            for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                                // Get the inventory item
                                InventoryItem item = itemSnapshot.getValue(InventoryItem.class);
                                if (item != null) {
                                    item.setId(itemSnapshot.getKey());

                                    // Log current quantity
                                    Log.d(TAG, "Current quantity: " + item.getQuantity());

                                    // Get request quantity and ensure it's a valid integer
                                    int requestQuantity = request.getQuantity();
                                    Log.d(TAG, "Request quantity: " + requestQuantity);

                                    // Update the quantity
                                    int newQuantity = item.getQuantity() + requestQuantity;
                                    Log.d(TAG, "New quantity will be: " + newQuantity);

                                    item.setQuantity(newQuantity);

                                    // Add history entry
                                    FirebaseUser currentUser = mAuth.getCurrentUser();
                                    String userEmail = currentUser != null ? currentUser.getEmail() : "Unknown";
                                    item.addHistoryEntry("Stock Added", userEmail,
                                            "Added " + requestQuantity + " from approved stock request for " +
                                            request.getBranch());

                                    // Update last updated timestamp
                                    item.setLastUpdated(System.currentTimeMillis());
                                    item.setLastUpdatedBy(userEmail);

                                    // Save the updated item
                                    inventoryRef.child(item.getId()).setValue(item)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "Inventory updated successfully for approved request. New quantity: " + newQuantity);

                                                // Show a toast to confirm the update
                                                Toast.makeText(OwnerDashboardActivity.this,
                                                    "Inventory updated: Added " + requestQuantity + " " + item.getName(),
                                                    Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Error updating inventory for approved request", e);

                                                // Show error toast
                                                Toast.makeText(OwnerDashboardActivity.this,
                                                    "Error updating inventory: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                            });

                                    break; // We found and updated the item, so break the loop
                                } else {
                                    Log.e(TAG, "Item is null after retrieval");
                                }
                            }
                        } else {
                            Log.e(TAG, "Item not found in inventory: " + request.getItemName());

                            // Show error toast
                            Toast.makeText(OwnerDashboardActivity.this,
                                "Error: Item '" + request.getItemName() + "' not found in inventory",
                                Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error querying inventory", error.toException());

                        // Show error toast
                        Toast.makeText(OwnerDashboardActivity.this,
                            "Error querying inventory: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
