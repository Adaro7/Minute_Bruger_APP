package com.example.minuteburgers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportsAdapter extends RecyclerView.Adapter<ReportsAdapter.ViewHolder> {
    private List<ReportItem> reportItems = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReportItem item = reportItems.get(position);
        holder.itemNameTextView.setText(item.getItemName());
        holder.actionTextView.setText(item.getAction());
        holder.userTextView.setText(item.getUser());
        holder.detailsTextView.setText(item.getDetails());
        holder.timestampTextView.setText(dateFormat.format(item.getTimestamp()));
    }

    @Override
    public int getItemCount() {
        return reportItems.size();
    }

    public void updateReports(List<ReportItem> newReports) {
        this.reportItems = newReports;
        notifyDataSetChanged();
    }

    public List<ReportItem> getReports() {
        return reportItems;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemNameTextView;
        TextView actionTextView;
        TextView userTextView;
        TextView detailsTextView;
        TextView timestampTextView;

        ViewHolder(View view) {
            super(view);
            itemNameTextView = view.findViewById(R.id.itemNameTextView);
            actionTextView = view.findViewById(R.id.actionTextView);
            userTextView = view.findViewById(R.id.userTextView);
            detailsTextView = view.findViewById(R.id.detailsTextView);
            timestampTextView = view.findViewById(R.id.timestampTextView);
        }
    }

    public static class ReportItem {
        private final String itemName;
        private final String action;
        private final String user;
        private final String details;
        private final long timestamp;

        public ReportItem(String itemName, String action, String user, String details, long timestamp) {
            this.itemName = itemName;
            this.action = action;
            this.user = user;
            this.details = details;
            this.timestamp = timestamp;
        }

        public String getItemName() { return itemName; }
        public String getAction() { return action; }
        public String getUser() { return user; }
        public String getDetails() { return details; }
        public long getTimestamp() { return timestamp; }
    }
} 