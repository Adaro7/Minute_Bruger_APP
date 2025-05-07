package com.example.minuteburgers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private List<InventoryItem.ItemHistory> historyList;

    public HistoryAdapter() {
        this.historyList = new ArrayList<>();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        InventoryItem.ItemHistory history = historyList.get(position);
        holder.actionText.setText(history.getAction());
        holder.userText.setText("By: " + history.getUser());
        holder.detailsText.setText(history.getDetails());
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String formattedDate = sdf.format(new Date(history.getTimestamp()));
        holder.timestampText.setText(formattedDate);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public void updateHistory(List<InventoryItem.ItemHistory> newHistory) {
        this.historyList = newHistory;
        notifyDataSetChanged();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView actionText;
        MaterialTextView userText;
        MaterialTextView detailsText;
        MaterialTextView timestampText;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            actionText = itemView.findViewById(R.id.historyAction);
            userText = itemView.findViewById(R.id.historyUser);
            detailsText = itemView.findViewById(R.id.historyDetails);
            timestampText = itemView.findViewById(R.id.historyTimestamp);
        }
    }
} 