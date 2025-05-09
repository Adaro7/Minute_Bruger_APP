package com.example.minuteburgers.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import com.example.minuteburgers.EmployeeDashboardActivity;
import com.example.minuteburgers.models.SalesRecord;
import com.example.minuteburgers.utils.NotificationHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.InventoryAdapter;
import com.example.minuteburgers.InventoryItem;
import com.example.minuteburgers.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class InventoryFragment extends Fragment {
    private static final String TAG = "InventoryFragment";

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private SearchView searchView;
    private TextView emptyStateText;
    private DatabaseReference dbRef;
    private ValueEventListener inventoryListener;

    public InventoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_inventory, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        dbRef = FirebaseDatabase.getInstance().getReference("inventory");

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerView);
        searchView = view.findViewById(R.id.searchView);
        emptyStateText = view.findViewById(R.id.emptyStateText);

        // Setup RecyclerView
        setupRecyclerView();

        // Setup search
        setupSearchView();

        // Load inventory
        loadInventory();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new InventoryAdapter(new ArrayList<>());

        // Set up item click listener for view-only mode (no edit/delete)
        adapter.setOnItemClickListener(new InventoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(InventoryItem item) {
                showItemDetails(item);
            }

            @Override
            public void onEditClick(InventoryItem item) {
                // Not used in employee view
            }

            @Override
            public void onDeleteClick(InventoryItem item) {
                // Not used in employee view
            }
        });

        // Set employee mode (view only)
        adapter.setEmployeeMode(true);

        recyclerView.setAdapter(adapter);
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

    private void loadInventory() {
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

                // Show empty state if no items
                if (items.isEmpty()) {
                    emptyStateText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyStateText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading inventory: ", error.toException());
                Toast.makeText(getContext(), "Error loading inventory", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchInventory(String query) {
        if (query.isEmpty()) {
            loadInventory();
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

                // Show empty state if no items found
                if (items.isEmpty()) {
                    emptyStateText.setText("No items found matching '" + query + "'");
                    emptyStateText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyStateText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error searching inventory: ", error.toException());
                Toast.makeText(getContext(), "Error searching inventory", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showItemDetails(InventoryItem item) {
        // Show item details dialog with option to deduct quantity (for employees)
        if (getActivity() != null) {
            // First, show the standard item details dialog
            ((ItemDetailListener) getActivity()).onShowItemDetail(item);

            // Then, show the deduct quantity option
            new android.os.Handler().postDelayed(() -> {
                showDeductQuantityDialog(item);
            }, 500); // Short delay to ensure the UI is ready
        }
    }

    /**
     * Show dialog to deduct quantity (mark as sold)
     */
    private void showDeductQuantityDialog(InventoryItem item) {
        if (getActivity() == null) return;

        // Create dialog view
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_deduct_quantity, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(dialogView);
        builder.setTitle("Mark Item as Sold");

        AlertDialog dialog = builder.create();

        // Initialize dialog views
        TextInputEditText quantityInput = dialogView.findViewById(R.id.quantityInput);
        TextInputEditText priceInput = dialogView.findViewById(R.id.priceInput);
        TextInputEditText notesInput = dialogView.findViewById(R.id.notesInput);
        MaterialButton deductButton = dialogView.findViewById(R.id.deductButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Set default price (can be changed by employee)
        priceInput.setText("0.00");

        // Set click listeners
        deductButton.setOnClickListener(v -> {
            // Validate inputs
            String quantityStr = quantityInput.getText().toString().trim();
            String priceStr = priceInput.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();

            if (quantityStr.isEmpty()) {
                Toast.makeText(getActivity(), "Please enter quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            if (priceStr.isEmpty()) {
                Toast.makeText(getActivity(), "Please enter price", Toast.LENGTH_SHORT).show();
                return;
            }

            int quantity;
            double price;

            try {
                quantity = Integer.parseInt(quantityStr);
                if (quantity <= 0) {
                    Toast.makeText(getActivity(), "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (quantity > item.getQuantity()) {
                    Toast.makeText(getActivity(), "Not enough stock available", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), "Please enter a valid quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                price = Double.parseDouble(priceStr);
                if (price < 0) {
                    Toast.makeText(getActivity(), "Price cannot be negative", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), "Please enter a valid price", Toast.LENGTH_SHORT).show();
                return;
            }

            // Deduct quantity and record sale
            deductQuantityAndRecordSale(item, quantity, price, notes, dialog);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Deduct quantity from inventory and record the sale
     */
    private void deductQuantityAndRecordSale(InventoryItem item, int quantity, double price, String notes, AlertDialog dialog) {
        if (getActivity() == null) return;

        // Show loading indicator
        Toast.makeText(getActivity(), "Processing sale...", Toast.LENGTH_SHORT).show();

        // Get current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getActivity(), "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get user details from Firestore
        FirebaseFirestore.getInstance().collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userName = documentSnapshot.getString("name");
                        String branch = documentSnapshot.getString("branch");

                        if (userName == null) userName = currentUser.getEmail();
                        if (branch == null) branch = "Main Branch";

                        // Create sales record
                        SalesRecord salesRecord = new SalesRecord(
                                item.getId(),
                                item.getName(),
                                quantity,
                                price,
                                currentUser.getUid(),
                                userName,
                                branch
                        );

                        // Add notes if provided
                        if (notes != null && !notes.isEmpty()) {
                            salesRecord.setNotes(notes);
                        }

                        // Save sales record to Firebase
                        FirebaseDatabase.getInstance().getReference("sales")
                                .push()
                                .setValue(salesRecord)
                                .addOnSuccessListener(aVoid -> {
                                    // Update inventory quantity
                                    updateInventoryAfterSale(item, quantity, currentUser.getEmail(), dialog);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error recording sale", e);
                                    Toast.makeText(getActivity(), "Error recording sale: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Toast.makeText(getActivity(), "User profile not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user profile", e);
                    Toast.makeText(getActivity(), "Error getting user profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Update inventory after a sale
     */
    private void updateInventoryAfterSale(InventoryItem item, int quantity, String userEmail, AlertDialog dialog) {
        // Get reference to inventory database
        DatabaseReference inventoryRef = FirebaseDatabase.getInstance().getReference("inventory")
                .child(item.getId());

        // Update the item
        int newQuantity = item.getQuantity() - quantity;

        // Create a map of updates
        Map<String, Object> updates = new HashMap<>();
        updates.put("quantity", newQuantity);
        updates.put("lastUpdated", System.currentTimeMillis());
        updates.put("lastUpdatedBy", userEmail);

        // Add history entry
        String historyEntry = "Sold " + quantity + " items";
        if (item.getHistory() == null) {
            item.setHistory(new ArrayList<>());
        }
        item.addHistoryEntry("Sale", userEmail, historyEntry);
        updates.put("history", item.getHistory());

        // Update the item in Firebase
        inventoryRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "Sale recorded successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        // Check if inventory is low after sale
                        if (newQuantity <= 5) {
                            // Show low inventory notification
                            NotificationHelper notificationHelper = new NotificationHelper(getActivity());
                            notificationHelper.sendLowInventoryNotification(item.getName(), newQuantity);

                            // Show low inventory alert
                            new AlertDialog.Builder(getActivity())
                                    .setTitle("Low Inventory Alert")
                                    .setMessage("The inventory for " + item.getName() + " is running low (only " + newQuantity + " left). Consider requesting more stock.")
                                    .setPositiveButton("Request Stock", (dialogInterface, i) -> {
                                        // Open stock request dialog
                                        if (getActivity() instanceof EmployeeDashboardActivity) {
                                            ((EmployeeDashboardActivity) getActivity()).showStockRequestDialog(item);
                                        }
                                    })
                                    .setNegativeButton("Later", null)
                                    .show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null) {
                        Log.e(TAG, "Error updating inventory after sale", e);
                        Toast.makeText(getActivity(), "Error updating inventory: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (inventoryListener != null) {
            dbRef.removeEventListener(inventoryListener);
        }
    }

    // Interface for communication with the activity
    public interface ItemDetailListener {
        void onShowItemDetail(InventoryItem item);
    }
}
