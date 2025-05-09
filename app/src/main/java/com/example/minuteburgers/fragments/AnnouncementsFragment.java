package com.example.minuteburgers.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.R;
import com.example.minuteburgers.adapters.AnnouncementAdapter;
import com.example.minuteburgers.models.Announcement;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnouncementsFragment extends Fragment {
    private static final String TAG = "AnnouncementsFragment";

    private RecyclerView recyclerView;
    private AnnouncementAdapter adapter;
    private TextView emptyStateText;
    private DatabaseReference announcementsRef;
    private List<Announcement> announcements = new ArrayList<>();

    public AnnouncementsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_announcements, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        announcementsRef = FirebaseDatabase.getInstance().getReference("announcements");

        // Initialize views
        recyclerView = view.findViewById(R.id.announcementsRecyclerView);
        emptyStateText = view.findViewById(R.id.emptyStateText);

        // Setup RecyclerView
        setupRecyclerView();

        // Load announcements
        loadAnnouncements();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AnnouncementAdapter(announcements, null);
        adapter.setOwnerMode(false); // Employee mode (view only)
        recyclerView.setAdapter(adapter);
    }

    private void loadAnnouncements() {
        announcementsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                announcements.clear();
                for (DataSnapshot announcementSnapshot : snapshot.getChildren()) {
                    Announcement announcement = announcementSnapshot.getValue(Announcement.class);
                    if (announcement != null) {
                        announcement.setId(announcementSnapshot.getKey());
                        announcements.add(announcement);
                    }
                }

                // Sort announcements by date (newest first)
                Collections.sort(announcements, (a1, a2) ->
                        a2.getCreatedAt().compareTo(a1.getCreatedAt()));

                adapter.notifyDataSetChanged();

                // Show empty state if no announcements
                if (announcements.isEmpty()) {
                    emptyStateText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyStateText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading announcements", error.toException());
                Toast.makeText(getContext(), "Error loading announcements", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
