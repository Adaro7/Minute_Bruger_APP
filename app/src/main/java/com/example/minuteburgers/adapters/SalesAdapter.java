package com.example.minuteburgers.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.R;
import com.example.minuteburgers.models.SalesRecord;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying sales records in a RecyclerView
 */
public class SalesAdapter extends RecyclerView.Adapter<SalesAdapter.SalesViewHolder> {
    private List<SalesRecord> salesRecords;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public SalesAdapter(List<SalesRecord> salesRecords) {
        this.salesRecords = salesRecords;
    }

    @NonNull
    @Override
    public SalesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sales_record, parent, false);
        return new SalesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SalesViewHolder holder, int position) {
        try {
            if (position < 0 || position >= salesRecords.size()) {
                return; // Prevent index out of bounds
            }

            SalesRecord record = salesRecords.get(position);
            if (record == null) {
                return; // Skip null records
            }

            // Set item name safely
            String itemName = record.getItemName();
            holder.itemNameText.setText(itemName != null ? itemName : "Unknown Item");

            // Set quantity safely
            holder.quantityText.setText(String.valueOf(record.getQuantity()));

            // Set price safely
            try {
                holder.priceText.setText(String.format(Locale.getDefault(), "₱%.2f", record.getUnitPrice()));
            } catch (Exception e) {
                holder.priceText.setText("₱0.00");
            }

            // Set total safely
            try {
                holder.totalText.setText(String.format(Locale.getDefault(), "₱%.2f", record.getTotalAmount()));
            } catch (Exception e) {
                holder.totalText.setText("₱0.00");
            }

            // Set date safely
            try {
                if (record.getSaleDate() != null) {
                    holder.dateText.setText(dateFormat.format(record.getSaleDate()));
                } else {
                    holder.dateText.setText("Unknown Date");
                }
            } catch (Exception e) {
                holder.dateText.setText("Invalid Date");
            }

            // Set sold by name safely
            String soldByName = record.getSoldByName();
            holder.soldByText.setText(soldByName != null ? soldByName : "Unknown");

            // Set branch safely
            String branch = record.getBranch();
            holder.branchText.setText(branch != null ? branch : "Unknown Branch");

            // Show notes if available
            String notes = record.getNotes();
            if (notes != null && !notes.isEmpty()) {
                holder.notesText.setVisibility(View.VISIBLE);
                holder.notesText.setText("Notes: " + notes);
            } else {
                holder.notesText.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            // Log error but don't crash
            android.util.Log.e("SalesAdapter", "Error binding view holder at position " + position, e);

            // Set default values
            holder.itemNameText.setText("Error loading item");
            holder.quantityText.setText("0");
            holder.priceText.setText("₱0.00");
            holder.totalText.setText("₱0.00");
            holder.dateText.setText("Unknown");
            holder.soldByText.setText("Unknown");
            holder.branchText.setText("Unknown");
            holder.notesText.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return salesRecords != null ? salesRecords.size() : 0;
    }

    /**
     * Update the adapter data with improved error handling
     *
     * @param newRecords The new list of sales records to display
     */
    public void updateData(List<SalesRecord> newRecords) {
        try {
            // Handle null input
            if (newRecords == null) {
                android.util.Log.w("SalesAdapter", "updateData called with null list, using empty list instead");
                this.salesRecords = new java.util.ArrayList<>();
            } else {
                this.salesRecords = newRecords;
            }

            // Notify adapter of changes
            notifyDataSetChanged();
        } catch (Exception e) {
            android.util.Log.e("SalesAdapter", "Error updating adapter data", e);
            // Ensure we have a valid list even if update fails
            if (this.salesRecords == null) {
                this.salesRecords = new java.util.ArrayList<>();
                notifyDataSetChanged();
            }
        }
    }

    /**
     * ViewHolder for sales records
     */
    static class SalesViewHolder extends RecyclerView.ViewHolder {
        TextView itemNameText;
        TextView quantityText;
        TextView priceText;
        TextView totalText;
        TextView dateText;
        TextView soldByText;
        TextView branchText;
        TextView notesText;

        public SalesViewHolder(@NonNull View itemView) {
            super(itemView);
            itemNameText = itemView.findViewById(R.id.itemNameText);
            quantityText = itemView.findViewById(R.id.quantityText);
            priceText = itemView.findViewById(R.id.priceText);
            totalText = itemView.findViewById(R.id.totalText);
            dateText = itemView.findViewById(R.id.dateText);
            soldByText = itemView.findViewById(R.id.soldByText);
            branchText = itemView.findViewById(R.id.branchText);
            notesText = itemView.findViewById(R.id.notesText);
        }
    }
}
