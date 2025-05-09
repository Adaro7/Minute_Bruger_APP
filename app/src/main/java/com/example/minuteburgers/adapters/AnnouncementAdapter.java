package com.example.minuteburgers.adapters;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.R;
import com.example.minuteburgers.models.Announcement;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder> {
    
    private final List<Announcement> announcements;
    private final OnAnnouncementClickListener listener;
    private boolean ownerMode = false;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
    
    public interface OnAnnouncementClickListener {
        void onAnnouncementClick(Announcement announcement);
    }
    
    public AnnouncementAdapter(List<Announcement> announcements, OnAnnouncementClickListener listener) {
        this.announcements = announcements;
        this.listener = listener;
    }
    
    public void setOwnerMode(boolean ownerMode) {
        this.ownerMode = ownerMode;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public AnnouncementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_announcement, parent, false);
        return new AnnouncementViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AnnouncementViewHolder holder, int position) {
        Announcement announcement = announcements.get(position);
        holder.bind(announcement, listener, ownerMode);
    }
    
    @Override
    public int getItemCount() {
        return announcements.size();
    }
    
    static class AnnouncementViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView titleText;
        private final TextView contentText;
        private final TextView dateText;
        private final TextView editHintText;
        
        public AnnouncementViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.announcementCard);
            titleText = itemView.findViewById(R.id.announcementTitle);
            contentText = itemView.findViewById(R.id.announcementContent);
            dateText = itemView.findViewById(R.id.announcementDate);
            editHintText = itemView.findViewById(R.id.editHintText);
        }
        
        public void bind(Announcement announcement, OnAnnouncementClickListener listener, boolean ownerMode) {
            titleText.setText(announcement.getTitle());
            contentText.setText(announcement.getContent());
            
            // Format date
            String dateStr;
            if (announcement.getCreatedAt() != null) {
                if (DateUtils.isToday(announcement.getCreatedAt().getTime())) {
                    dateStr = "Today at " + new SimpleDateFormat("hh:mm a", Locale.getDefault())
                            .format(announcement.getCreatedAt());
                } else {
                    dateStr = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                            .format(announcement.getCreatedAt());
                }
                dateText.setText(dateStr);
            } else {
                dateText.setText("Unknown date");
            }
            
            // Show edit hint if in owner mode
            editHintText.setVisibility(ownerMode ? View.VISIBLE : View.GONE);
            
            // Set click listener
            if (ownerMode) {
                cardView.setOnClickListener(v -> listener.onAnnouncementClick(announcement));
            } else {
                cardView.setClickable(false);
            }
        }
    }
}
