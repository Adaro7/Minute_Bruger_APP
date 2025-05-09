package com.example.minuteburgers;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.adapters.AnnouncementAdapter;
import com.example.minuteburgers.models.Announcement;
import com.example.minuteburgers.utils.EmailSender;
import com.example.minuteburgers.utils.PermissionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class AnnouncementsActivity extends AppCompatActivity {
    private static final String TAG = "AnnouncementsActivity";

    private RecyclerView recyclerView;
    private AnnouncementAdapter adapter;
    private List<Announcement> announcements = new ArrayList<>();
    private FloatingActionButton addAnnouncementButton;
    private MaterialButton backButton;
    private LinearProgressIndicator progressIndicator;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DatabaseReference announcementsRef;
    private PermissionManager permissionManager;
    private boolean isOwner = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcements);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        announcementsRef = FirebaseDatabase.getInstance().getReference("announcements");

        // Initialize permission manager
        permissionManager = PermissionManager.getInstance();

        // Check permissions
        checkPermissions();

        // Initialize views
        initializeViews();

        // Load announcements
        loadAnnouncements();
    }

    private void checkPermissions() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Load permissions
            permissionManager.loadUserPermissions(mAuth, db, success -> {
                if (success) {
                    // Check if user is owner
                    isOwner = "owner".equals(permissionManager.getUserRole());

                    // Update UI based on role
                    if (addAnnouncementButton != null) {
                        addAnnouncementButton.setVisibility(isOwner ? View.VISIBLE : View.GONE);
                    }

                    if (adapter != null) {
                        adapter.setOwnerMode(isOwner);
                    }
                } else {
                    // Failed to load permissions
                    Log.e(TAG, "Failed to load permissions");
                    Toast.makeText(AnnouncementsActivity.this,
                            "Error checking permissions",
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // User not logged in
            Toast.makeText(this, "Please log in to continue", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.announcementsRecyclerView);
        addAnnouncementButton = findViewById(R.id.addAnnouncementButton);
        backButton = findViewById(R.id.backButton);
        progressIndicator = findViewById(R.id.progressIndicator);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AnnouncementAdapter(announcements, announcement -> {
            if (isOwner) {
                showEditAnnouncementDialog(announcement);
            }
        });
        adapter.setOwnerMode(isOwner);
        recyclerView.setAdapter(adapter);

        // Setup click listeners
        addAnnouncementButton.setOnClickListener(v -> showAddAnnouncementDialog());
        backButton.setOnClickListener(v -> finish());

        // Hide add button if not owner
        addAnnouncementButton.setVisibility(isOwner ? View.VISIBLE : View.GONE);
    }

    private void loadAnnouncements() {
        showLoading(true);

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
                showLoading(false);

                if (announcements.isEmpty()) {
                    Toast.makeText(AnnouncementsActivity.this,
                            "No announcements found",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading announcements", error.toException());
                Toast.makeText(AnnouncementsActivity.this,
                        "Error loading announcements",
                        Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void showAddAnnouncementDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_announcement, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("Add New Announcement");

        AlertDialog dialog = builder.create();

        // Initialize dialog views
        TextInputEditText titleInput = dialogView.findViewById(R.id.announcementTitleInput);
        TextInputEditText contentInput = dialogView.findViewById(R.id.announcementContentInput);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);

        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String content = contentInput.getText().toString().trim();

            if (title.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create and save the announcement
            createAnnouncement(title, content, dialog);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditAnnouncementDialog(Announcement announcement) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_announcement, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("Edit Announcement");

        AlertDialog dialog = builder.create();

        // Initialize dialog views
        TextInputEditText titleInput = dialogView.findViewById(R.id.announcementTitleInput);
        TextInputEditText contentInput = dialogView.findViewById(R.id.announcementContentInput);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton deleteButton = dialogView.findViewById(R.id.deleteButton);

        // Set existing values
        titleInput.setText(announcement.getTitle());
        contentInput.setText(announcement.getContent());

        // Show delete button
        deleteButton.setVisibility(View.VISIBLE);

        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String content = contentInput.getText().toString().trim();

            if (title.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update the announcement
            updateAnnouncement(announcement.getId(), title, content, dialog);
        });

        deleteButton.setOnClickListener(v -> {
            // Show confirmation dialog
            new AlertDialog.Builder(this)
                    .setTitle("Delete Announcement")
                    .setMessage("Are you sure you want to delete this announcement?")
                    .setPositiveButton("Delete", (dialogInterface, i) -> {
                        deleteAnnouncement(announcement.getId(), dialog);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void createAnnouncement(String title, String content, AlertDialog dialog) {
        showLoading(true);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            Toast.makeText(this, "You must be logged in to create announcements", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create announcement object
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setCreatedBy(user.getUid());
        announcement.setCreatedAt(new Date());

        // Save to Firebase
        announcementsRef.push().setValue(announcement)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    dialog.dismiss();
                    Toast.makeText(AnnouncementsActivity.this,
                            "Announcement created successfully",
                            Toast.LENGTH_SHORT).show();

                    // Send announcement to all employees via email
                    if (isOwner) {
                        // Ask user if they want to send email
                        new AlertDialog.Builder(AnnouncementsActivity.this)
                            .setTitle("Send Email")
                            .setMessage("Do you want to send this announcement to all employees via email?")
                            .setPositiveButton("Yes", (dialogInterface, i) -> {
                                EmailSender.sendAnnouncementToEmployees(
                                    AnnouncementsActivity.this,
                                    title,
                                    content
                                );
                            })
                            .setNegativeButton("No", null)
                            .show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error creating announcement", e);
                    Toast.makeText(AnnouncementsActivity.this,
                            "Error creating announcement: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateAnnouncement(String id, String title, String content, AlertDialog dialog) {
        showLoading(true);

        // Update announcement
        announcementsRef.child(id).child("title").setValue(title);
        announcementsRef.child(id).child("content").setValue(content);
        announcementsRef.child(id).child("updatedAt").setValue(new Date())
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    dialog.dismiss();
                    Toast.makeText(AnnouncementsActivity.this,
                            "Announcement updated successfully",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error updating announcement", e);
                    Toast.makeText(AnnouncementsActivity.this,
                            "Error updating announcement: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteAnnouncement(String id, AlertDialog dialog) {
        showLoading(true);

        // Delete announcement
        announcementsRef.child(id).removeValue()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    dialog.dismiss();
                    Toast.makeText(AnnouncementsActivity.this,
                            "Announcement deleted successfully",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error deleting announcement", e);
                    Toast.makeText(AnnouncementsActivity.this,
                            "Error deleting announcement: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        addAnnouncementButton.setEnabled(!show);
    }
}
