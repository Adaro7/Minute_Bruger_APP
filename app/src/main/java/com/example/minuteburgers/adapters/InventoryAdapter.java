package com.example.minuteburgers.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.minuteburgers.InventoryItem;
import com.example.minuteburgers.R;

import java.util.ArrayList;
import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {
    
    private List<InventoryItem> items;
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(InventoryItem item);
        void onEditClick(InventoryItem item);
        void onDeleteClick(InventoryItem item);
    }
    
    public InventoryAdapter(List<InventoryItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    public void updateItems(List<InventoryItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = items.get(position);
        
        // Set item name
        holder.itemNameText.setText(item.getName());
        
        // Set quantity
        holder.itemQuantityText.setText("Quantity: " + item.getQuantity());
        
        // Set branch
        holder.itemBranchText.setText("Branch: " + (item.getBranch() != null ? item.getBranch() : "All Branches"));
        
        // Load image if available
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(item.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.itemImage);
        } else {
            holder.itemImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
        
        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(item);
            }
        });
        
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(item);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView itemImage;
        TextView itemNameText;
        TextView itemQuantityText;
        TextView itemBranchText;
        ImageButton editButton;
        ImageButton deleteButton;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.itemImage);
            itemNameText = itemView.findViewById(R.id.itemNameText);
            itemQuantityText = itemView.findViewById(R.id.itemQuantityText);
            itemBranchText = itemView.findViewById(R.id.itemBranchText);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
