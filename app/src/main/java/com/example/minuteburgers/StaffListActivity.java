package com.example.minuteburgers;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StaffListActivity extends AppCompatActivity {
    private static final String TAG = "StaffListActivity";
    private RecyclerView staffRecyclerView;
    private StaffAdapter staffAdapter;
    private LinearProgressIndicator progressIndicator;
    private List<StaffMember> currentStaffList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_list);
        initializeViews();
        loadStaffList();
    }

    private void initializeViews() {
        staffRecyclerView = findViewById(R.id.staffRecyclerView);
        progressIndicator = findViewById(R.id.progressIndicator);
        MaterialButton backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        staffRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        staffAdapter = new StaffAdapter();
        staffRecyclerView.setAdapter(staffAdapter);
    }

    private void loadStaffList() {
        progressIndicator.setVisibility(View.VISIBLE);

        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "staff")  // Only get users with staff role
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                currentStaffList.clear();
                Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " staff members");
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String name = document.getString("name");
                    String email = document.getString("email");
                    String role = document.getString("role");
                    
                    Log.d(TAG, "Processing user: " + email + " with role: " + role);
                    
                    Date registrationDate = document.getTimestamp("registrationDate") != null ?
                        document.getTimestamp("registrationDate").toDate() :
                        new Date(document.getLong("createdAt") != null ? 
                            document.getLong("createdAt") : System.currentTimeMillis());

                    if (email != null) {
                        String displayName = (name != null && !name.isEmpty()) ? name : email;
                        currentStaffList.add(new StaffMember(displayName, email, registrationDate));
                        Log.d(TAG, "Added staff member: " + displayName);
                    }
                }

                staffAdapter.updateStaffList(currentStaffList);
                progressIndicator.setVisibility(View.GONE);

                if (currentStaffList.isEmpty()) {
                    Toast.makeText(this, "No staff members found", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "Total staff members loaded: " + currentStaffList.size());
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading staff list: ", e);
                progressIndicator.setVisibility(View.GONE);
                Toast.makeText(this, "Error loading staff list", Toast.LENGTH_SHORT).show();
            });
    }

    private static class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.StaffViewHolder> {
        private List<StaffMember> staffList = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        @NonNull
        @Override
        public StaffViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_staff, parent, false);
            return new StaffViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StaffViewHolder holder, int position) {
            StaffMember staff = staffList.get(position);
            holder.nameText.setText(staff.name);
            holder.emailText.setText(staff.email);
            holder.dateText.setText("Registered: " + dateFormat.format(staff.registrationDate));
        }

        @Override
        public int getItemCount() {
            return staffList.size();
        }

        public void updateStaffList(List<StaffMember> newList) {
            this.staffList = new ArrayList<>(newList);
            notifyDataSetChanged();
        }

        static class StaffViewHolder extends RecyclerView.ViewHolder {
            final com.google.android.material.textview.MaterialTextView nameText;
            final com.google.android.material.textview.MaterialTextView emailText;
            final com.google.android.material.textview.MaterialTextView dateText;

            StaffViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.staffName);
                emailText = itemView.findViewById(R.id.staffEmail);
                dateText = itemView.findViewById(R.id.registrationDate);
            }
        }
    }

    private static class StaffMember {
        final String name;
        final String email;
        final Date registrationDate;

        StaffMember(String name, String email, Date registrationDate) {
            this.name = name;
            this.email = email;
            this.registrationDate = registrationDate;
        }
    }
} 