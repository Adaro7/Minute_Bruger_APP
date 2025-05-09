package com.example.minuteburgers;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {
    private Context context;
    private List<InventoryItem> items;
    private OnItemClickListener listener;
    private boolean employeeMode = false; // Default to owner mode

    public interface OnItemClickListener {
        void onItemClick(InventoryItem item);
        void onEditClick(InventoryItem item);
        void onDeleteClick(InventoryItem item);
    }

    public InventoryAdapter(List<InventoryItem> items) {
        this.items = items;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = items.get(position);
        holder.itemName.setText(item.getName());
        holder.itemQuantity.setText("Qty: " + item.getQuantity());

        // Load image using Glide with improved error handling and logging
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Log.d("InventoryAdapter", "Loading image URL: " + item.getImageUrl());
            try {
                Glide.with(context)
                    .load(item.getImageUrl())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .centerCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                  Target<Drawable> target, boolean isFirstResource) {
                            Log.e("InventoryAdapter", "Image load failed for URL: " + item.getImageUrl(), e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                     Target<Drawable> target, DataSource dataSource,
                                                     boolean isFirstResource) {
                            Log.d("InventoryAdapter", "Image loaded successfully: " + item.getImageUrl());
                            return false;
                        }
                    })
                    .timeout(60000) // Increase timeout to 60 seconds
                    .into(holder.itemImage);
            } catch (Exception e) {
                Log.e("InventoryAdapter", "Error loading image: " + e.getMessage(), e);
                holder.itemImage.setImageResource(R.drawable.error_image);
            }
        } else {
            Log.d("InventoryAdapter", "No image URL for item: " + item.getName());
            holder.itemImage.setImageResource(R.drawable.placeholder_image);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });

        // Show or hide edit/delete buttons based on mode
        if (employeeMode) {
            // Employee mode - hide edit and delete buttons
            holder.editButton.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.GONE);
        } else {
            // Owner mode - show edit and delete buttons
            holder.editButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.VISIBLE);

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
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItems(List<InventoryItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    /**
     * Set the adapter to employee mode (view only) or owner mode (with edit/delete)
     * @param employeeMode true for employee mode, false for owner mode
     */
    public void setEmployeeMode(boolean employeeMode) {
        this.employeeMode = employeeMode;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView itemImage;
        TextView itemName;
        TextView itemQuantity;
        ImageButton editButton;
        ImageButton deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.itemImage);
            itemName = itemView.findViewById(R.id.itemNameText);
            itemQuantity = itemView.findViewById(R.id.itemQuantityText);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}