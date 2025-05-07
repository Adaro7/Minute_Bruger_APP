package com.example.minuteburgers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.PageSize;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ReportsActivity extends AppCompatActivity {
    private static final String TAG = "ReportsActivity";

    private RecyclerView reportsRecyclerView;
    private ReportsAdapter reportsAdapter;
    private LinearProgressIndicator progressIndicator;
    private MaterialButton exportPdfButton;
    private AutoCompleteTextView dateRangeSpinner;
    private AutoCompleteTextView userFilterSpinner;

    private List<ReportsAdapter.ReportItem> allReports = new ArrayList<>();
    private String selectedDateRange = "All Time";
    private String selectedUser = "All Users";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        initializeViews();
        setupSpinners();
        setupRecyclerView();
        loadReports();
    }

    private void initializeViews() {
        reportsRecyclerView = findViewById(R.id.reportsRecyclerView);
        progressIndicator = findViewById(R.id.progressIndicator);
        exportPdfButton = findViewById(R.id.exportPdfButton);
        dateRangeSpinner = findViewById(R.id.dateRangeSpinner);
        userFilterSpinner = findViewById(R.id.userFilterSpinner);

        MaterialButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        exportPdfButton.setOnClickListener(v -> sendReportEmail());
    }

    private void setupSpinners() {
        // Date range options
        String[] dateRanges = {"All Time", "Today", "Last 7 Days", "Last 30 Days"};
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, dateRanges);
        dateRangeSpinner.setAdapter(dateAdapter);
        dateRangeSpinner.setText(selectedDateRange, false);
        dateRangeSpinner.setOnItemClickListener((parent, view, position, id) -> {
            selectedDateRange = dateRanges[position];
            filterReports();
        });

        // User filter will be populated after loading reports
    }

    private void setupRecyclerView() {
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        reportsAdapter = new ReportsAdapter();
        reportsRecyclerView.setAdapter(reportsAdapter);
    }

    private void loadReports() {
        progressIndicator.setVisibility(View.VISIBLE);
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("inventory");
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        // First, fetch all users to get their full names
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                // Create a map of email to full name
                Map<String, String> emailToNameMap = new HashMap<>();
                for (DataSnapshot userSnapshot : usersSnapshot.getChildren()) {
                    String email = userSnapshot.child("email").getValue(String.class);
                    String name = userSnapshot.child("name").getValue(String.class);
                    if (email != null && name != null) {
                        emailToNameMap.put(email, name);
                    }
                }

                // Now fetch the reports
                dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        allReports.clear();
                        Set<String> uniqueUsers = new HashSet<>();
                        uniqueUsers.add("All Users");

                        for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                            InventoryItem item = itemSnapshot.getValue(InventoryItem.class);
                            if (item != null && item.getHistory() != null) {
                                for (InventoryItem.ItemHistory history : item.getHistory()) {
                                    String fullName = emailToNameMap.getOrDefault(history.getUser(), history.getUser());
                                    ReportsAdapter.ReportItem reportItem = new ReportsAdapter.ReportItem(
                                        item.getName(),
                                        history.getAction(),
                                        fullName,  // Use full name if available, otherwise use email
                                        history.getDetails(),
                                        history.getTimestamp()
                                    );
                                    allReports.add(reportItem);
                                    uniqueUsers.add(fullName);
                                }
                            }
                        }

                        // Sort by timestamp (newest first)
                        Collections.sort(allReports, (o1, o2) -> Long.compare(o2.getTimestamp(), o1.getTimestamp()));

                        // Convert Set to List and sort alphabetically (keeping "All Users" at the top)
                        List<String> users = new ArrayList<>(uniqueUsers);
                        users.remove("All Users");
                        Collections.sort(users);  // Sort alphabetically
                        users.add(0, "All Users");  // Add back at the beginning

                        // Setup user filter
                        ArrayAdapter<String> userAdapter = new ArrayAdapter<>(
                            ReportsActivity.this, android.R.layout.simple_dropdown_item_1line, users);
                        userFilterSpinner.setAdapter(userAdapter);
                        userFilterSpinner.setText(selectedUser, false);
                        userFilterSpinner.setOnItemClickListener((parent, view, position, id) -> {
                            selectedUser = users.get(position);
                            filterReports();
                        });

                        filterReports();
                        progressIndicator.setVisibility(View.GONE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading reports: ", error.toException());
                        progressIndicator.setVisibility(View.GONE);
                        Toast.makeText(ReportsActivity.this, "Error loading reports", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading users: ", error.toException());
                progressIndicator.setVisibility(View.GONE);
                Toast.makeText(ReportsActivity.this, "Error loading users", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterReports() {
        List<ReportsAdapter.ReportItem> filteredItems = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        long timeFilter = 0;

        switch (selectedDateRange) {
            case "Today":
                timeFilter = currentTime - (24 * 60 * 60 * 1000);
                break;
            case "Last 7 Days":
                timeFilter = currentTime - (7 * 24 * 60 * 60 * 1000);
                break;
            case "Last 30 Days":
                timeFilter = currentTime - (30 * 24 * 60 * 60 * 1000);
                break;
        }

        for (ReportsAdapter.ReportItem item : allReports) {
            boolean matchesTimeFilter = selectedDateRange.equals("All Time") || item.getTimestamp() >= timeFilter;
            boolean matchesUserFilter = selectedUser.equals("All Users") || item.getUser().equals(selectedUser);

            if (matchesTimeFilter && matchesUserFilter) {
                filteredItems.add(item);
            }
        }

        reportsAdapter.updateReports(filteredItems);
    }

    private void sendReportEmail() {
        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (currentUserEmail == null) {
            Toast.makeText(this, "No email address found", Toast.LENGTH_SHORT).show();
            return;
        }

        File pdfFile = null;
        FileOutputStream fos = null;
        Document document = null;

        try {
            // Create PDF file in cache directory
            pdfFile = new File(getCacheDir(), "inventory_report.pdf");
            fos = new FileOutputStream(pdfFile);
            document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, fos);

            document.open();
            
            // Add logo and title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Minute Burgers Inventory Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Add report info
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            
            Paragraph info = new Paragraph();
            info.add(new Phrase("Generated for: ", boldFont));
            info.add(new Phrase(currentUserEmail + "\n", normalFont));
            info.add(new Phrase("Date Range: ", boldFont));
            info.add(new Phrase(selectedDateRange + "\n", normalFont));
            document.add(info);

            // Create table
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            float[] columnWidths = {2f, 1.5f, 2f, 3f, 2f};
            table.setWidths(columnWidths);

            // Add headers
            String[] headers = {"Item", "Action", "Details", "Time"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, boldFont));
                cell.setBackgroundColor(new com.itextpdf.text.BaseColor(255, 140, 0));
                cell.setPadding(8);
                table.addCell(cell);
            }

            // Add data
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            List<ReportsAdapter.ReportItem> reports = reportsAdapter.getReports();
            if (reports.isEmpty()) {
                document.add(new Paragraph("No reports found for the selected filters.", normalFont));
            } else {
                for (ReportsAdapter.ReportItem item : reports) {
                    table.addCell(new Phrase(item.getItemName(), normalFont));
                    table.addCell(new Phrase(item.getAction(), normalFont));
                    table.addCell(new Phrase(item.getDetails(), normalFont));
                    table.addCell(new Phrase(sdf.format(new Date(item.getTimestamp())), normalFont));
                }
                document.add(table);
            }

            document.close();

            // Create email intent with PDF attachment
            Uri pdfUri = FileProvider.getUriForFile(this, 
                "com.example.minuteburgers.fileprovider", pdfFile);

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("application/pdf");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{currentUserEmail});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Minute Burgers Inventory Report");
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Please find attached the inventory report.");
            emailIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(emailIntent, "Send report via..."));

        } catch (Exception e) {
            Log.e(TAG, "Error creating PDF: " + e.getMessage(), e);
            Toast.makeText(this, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file stream", e);
                }
            }
        }
    }
} 