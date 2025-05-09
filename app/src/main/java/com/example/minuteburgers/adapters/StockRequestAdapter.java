package com.example.minuteburgers.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.R;
import com.example.minuteburgers.models.StockRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class StockRequestAdapter extends RecyclerView.Adapter<StockRequestAdapter.StockRequestViewHolder> {
    private List<StockRequest> requests;
    private RequestActionListener actionListener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public interface RequestActionListener {
        void onRequestAction(StockRequest request, String action);
    }

    public StockRequestAdapter(List<StockRequest> requests, RequestActionListener actionListener) {
        this.requests = requests;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public StockRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_request, parent, false);
        return new StockRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StockRequestViewHolder holder, int position) {
        StockRequest request = requests.get(position);
        holder.bind(request, actionListener);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class StockRequestViewHolder extends RecyclerView.ViewHolder {
        private TextView itemNameTextView;
        private TextView quantityTextView;
        private TextView requestedByTextView;
        private TextView branchTextView;
        private TextView dateTextView;
        private Chip statusChip;
        private TextView notesTextView;
        private MaterialButton approveButton;
        private MaterialButton rejectButton;
        private View actionButtonsLayout;

        public StockRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            itemNameTextView = itemView.findViewById(R.id.itemName);
            quantityTextView = itemView.findViewById(R.id.quantity);
            requestedByTextView = itemView.findViewById(R.id.requestedBy);
            branchTextView = itemView.findViewById(R.id.branch);
            dateTextView = itemView.findViewById(R.id.requestDate);
            statusChip = itemView.findViewById(R.id.status);
            notesTextView = itemView.findViewById(R.id.notes);
            approveButton = itemView.findViewById(R.id.approveButton);
            rejectButton = itemView.findViewById(R.id.rejectButton);
            actionButtonsLayout = itemView.findViewById(R.id.actionButtonsLayout);
        }

        public void bind(StockRequest request, RequestActionListener actionListener) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

            // Set basic info
            itemNameTextView.setText(request.getItemName());
            quantityTextView.setText("Quantity: " + request.getQuantity());
            requestedByTextView.setText("Requested by: " + request.getRequestedByName());
            branchTextView.setText("Branch: " + request.getBranch());
            dateTextView.setText("Date: " + dateFormat.format(request.getRequestDate()));

            // Set status chip
            statusChip.setText(request.getStatus().toUpperCase());

            // Set notes if available
            if (request.getNotes() != null && !request.getNotes().isEmpty()) {
                notesTextView.setVisibility(View.VISIBLE);
                notesTextView.setText("Notes: " + request.getNotes());
            } else {
                notesTextView.setVisibility(View.GONE);
            }

            // Show/hide action buttons based on status
            if (request.isPending()) {
                actionButtonsLayout.setVisibility(View.VISIBLE);
                approveButton.setOnClickListener(v ->
                        actionListener.onRequestAction(request, "approved"));
                rejectButton.setOnClickListener(v ->
                        actionListener.onRequestAction(request, "rejected"));
            } else {
                actionButtonsLayout.setVisibility(View.GONE);
            }

            // Get resources
            android.content.res.Resources resources = itemView.getContext().getResources();

            // Set status indicator and chip color based on status
            View statusIndicator = itemView.findViewById(R.id.statusIndicator);

            if (request.isApproved()) {
                statusChip.setChipBackgroundColor(ColorStateList.valueOf(
                        resources.getColor(R.color.success_green)));
                statusIndicator.setBackgroundColor(resources.getColor(R.color.success_green));
            } else if (request.isRejected()) {
                statusChip.setChipBackgroundColor(ColorStateList.valueOf(
                        resources.getColor(R.color.error_red)));
                statusIndicator.setBackgroundColor(resources.getColor(R.color.error_red));
            } else {
                statusChip.setChipBackgroundColor(ColorStateList.valueOf(
                        resources.getColor(R.color.warning_yellow)));
                statusIndicator.setBackgroundColor(resources.getColor(R.color.warning_yellow));
            }
        }
    }
}
