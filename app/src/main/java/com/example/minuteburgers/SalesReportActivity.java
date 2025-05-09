package com.example.minuteburgers;

import android.app.DatePickerDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.adapters.SalesAdapter;
import com.example.minuteburgers.models.SalesRecord;
import com.example.minuteburgers.utils.PDFReportGenerator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SalesReportActivity extends AppCompatActivity {
    private static final String TAG = "SalesReportActivity";

    private DatabaseReference salesRef;
    private List<SalesRecord> salesRecords = new ArrayList<>();
    private SalesAdapter adapter;
    private List<String> branches = new ArrayList<>();
    private String selectedBranch = null; // null means all branches

    private Date startDate;
    private Date endDate;

    // UI components
    private RecyclerView salesRecyclerView;
    private Button startDateButton;
    private Button endDateButton;
    private Button generateReportButton;
    private Button backButton;
    private Spinner branchSpinner;
    private ProgressBar progressBar;
    private TextView totalItemsText;
    private TextView totalAmountText;
    private TextView recordCountText;

    // Download manager
    private DownloadManager downloadManager;
    private long downloadId = -1;
    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_sales_report);

            Log.d(TAG, "SalesReportActivity onCreate started");

            // Initialize UI components with error handling
            try {
                initializeUIComponents();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing UI components", e);
                Toast.makeText(this, "Error initializing UI: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish(); // Close the activity if UI initialization fails
                return;
            }

            // Initialize Firebase
            try {
                salesRef = FirebaseDatabase.getInstance().getReference("sales");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Firebase", e);
                Toast.makeText(this, "Error connecting to database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Set up RecyclerView
            try {
                adapter = new SalesAdapter(salesRecords);
                salesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                salesRecyclerView.setAdapter(adapter);
            } catch (Exception e) {
                Log.e(TAG, "Error setting up RecyclerView", e);
                Toast.makeText(this, "Error setting up sales list: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            // Set up date pickers
            try {
                setupDatePickers();
            } catch (Exception e) {
                Log.e(TAG, "Error setting up date pickers", e);
                Toast.makeText(this, "Error setting up date filters: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            // Set up branch spinner
            try {
                setupBranchSpinner();
            } catch (Exception e) {
                Log.e(TAG, "Error setting up branch spinner", e);
                Toast.makeText(this, "Error setting up branch filter: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            // Set up buttons
            try {
                generateReportButton.setOnClickListener(v -> generateReport());
                backButton.setOnClickListener(v -> finish());
            } catch (Exception e) {
                Log.e(TAG, "Error setting up button listeners", e);
            }

            // Initialize download manager
            try {
                downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                setupDownloadReceiver();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing download manager", e);
                Toast.makeText(this, "PDF download functionality may not work: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            // Load sales data
            try {
                loadSalesData();
            } catch (Exception e) {
                Log.e(TAG, "Error loading sales data", e);
                Toast.makeText(this, "Error loading sales data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                progressBar.setVisibility(View.GONE);
            }

            Log.d(TAG, "SalesReportActivity onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in SalesReportActivity onCreate", e);
            Toast.makeText(this, "Error initializing Sales Report: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Initialize all UI components
     */
    private void initializeUIComponents() {
        salesRecyclerView = findViewById(R.id.salesRecyclerView);
        startDateButton = findViewById(R.id.startDateButton);
        endDateButton = findViewById(R.id.endDateButton);
        generateReportButton = findViewById(R.id.generateReportButton);
        backButton = findViewById(R.id.backButton);
        branchSpinner = findViewById(R.id.branchSpinner);
        progressBar = findViewById(R.id.progressBar);
        totalItemsText = findViewById(R.id.totalItemsText);
        totalAmountText = findViewById(R.id.totalAmountText);
        recordCountText = findViewById(R.id.recordCountText);
    }

    /**
     * Set up the download completion receiver
     */
    private void setupDownloadReceiver() {
        // Set up download completion receiver
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        Toast.makeText(context, "Report downloaded successfully", Toast.LENGTH_LONG).show();

                        // Query the download status
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(id);
                        android.database.Cursor cursor = downloadManager.query(query);

                        if (cursor.moveToFirst()) {
                            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                            int status = cursor.getInt(statusIndex);
                            int reason = cursor.getInt(reasonIndex);

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                                String uriString = cursor.getString(uriIndex);
                                Log.d(TAG, "Download completed: " + uriString);
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                Log.e(TAG, "Download failed with reason: " + reason);
                                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                        cursor.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in download receiver", e);
                }
            }
        };

        // Register the download receiver with compatibility for different Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+ (API 33+), use the RECEIVER_NOT_EXPORTED flag
            registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            // For older Android versions, use the standard registration
            registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private void setupDatePickers() {
        // Set default date range to current month
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        startDate = calendar.getTime();

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        endDate = calendar.getTime();

        // Format dates for display
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        startDateButton.setText(dateFormat.format(startDate));
        endDateButton.setText(dateFormat.format(endDate));

        // Set up date picker dialogs
        startDateButton.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar newDate = Calendar.getInstance();
                        newDate.set(year, month, dayOfMonth);
                        startDate = newDate.getTime();
                        startDateButton.setText(dateFormat.format(startDate));
                        filterSalesData();
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        endDateButton.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTime(endDate);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar newDate = Calendar.getInstance();
                        newDate.set(year, month, dayOfMonth);
                        endDate = newDate.getTime();
                        endDateButton.setText(dateFormat.format(endDate));
                        filterSalesData();
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });
    }

    private void setupBranchSpinner() {
        // Add "All Branches" option
        branches.add("All Branches");

        // Create adapter for spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, branches);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        branchSpinner.setAdapter(spinnerAdapter);

        // Set listener
        branchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // "All Branches" selected
                    selectedBranch = null;
                } else {
                    selectedBranch = branches.get(position);
                }
                filterSalesData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedBranch = null;
            }
        });

        // Load branches from Firebase
        FirebaseDatabase.getInstance().getReference("users")
                .orderByChild("role").equalTo("employee")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String branch = userSnapshot.child("branch").getValue(String.class);
                            if (branch != null && !branch.isEmpty() && !branches.contains(branch)) {
                                branches.add(branch);
                            }
                        }
                        spinnerAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading branches", error.toException());
                    }
                });
    }

    /**
     * Load sales data from Firebase with improved error handling
     */
    private void loadSalesData() {
        try {
            progressBar.setVisibility(View.VISIBLE);

            // Check if salesRef is initialized
            if (salesRef == null) {
                Log.e(TAG, "Sales reference is null, reinitializing");
                salesRef = FirebaseDatabase.getInstance().getReference("sales");

                if (salesRef == null) {
                    throw new IllegalStateException("Failed to initialize sales reference");
                }
            }

            salesRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        Log.d(TAG, "Sales data received, processing...");
                        salesRecords.clear();
                        int recordCount = 0;

                        for (DataSnapshot recordSnapshot : snapshot.getChildren()) {
                            try {
                                SalesRecord record = recordSnapshot.getValue(SalesRecord.class);
                                if (record != null) {
                                    record.setId(recordSnapshot.getKey());
                                    salesRecords.add(record);
                                    recordCount++;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing sales record: " + recordSnapshot.getKey(), e);
                                // Continue processing other records
                            }
                        }

                        Log.d(TAG, "Processed " + recordCount + " sales records");

                        // Update UI with the data
                        filterSalesData();
                        progressBar.setVisibility(View.GONE);

                        // Show a message if no records were found
                        if (salesRecords.isEmpty()) {
                            Toast.makeText(SalesReportActivity.this,
                                "No sales records found",
                                Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing sales data", e);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(SalesReportActivity.this,
                                "Error processing sales data: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error loading sales data", error.toException());
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SalesReportActivity.this,
                            "Error loading sales data: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in loadSalesData", e);
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this,
                "Error loading sales data: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Filter sales data based on selected date range and branch
     * with improved error handling
     */
    private void filterSalesData() {
        try {
            List<SalesRecord> filteredRecords = new ArrayList<>();

            // Validate date range
            if (startDate == null || endDate == null) {
                Log.e(TAG, "Date range is null, setting default values");
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                startDate = calendar.getTime();

                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                endDate = calendar.getTime();
            }

            // Log filter criteria
            Log.d(TAG, "Filtering sales data - Date range: " + startDate + " to " + endDate +
                  ", Branch: " + (selectedBranch != null ? selectedBranch : "All Branches"));

            // Check if salesRecords is initialized
            if (salesRecords == null) {
                Log.e(TAG, "Sales records list is null, initializing empty list");
                salesRecords = new ArrayList<>();
            }

            for (SalesRecord record : salesRecords) {
                try {
                    // Validate record
                    if (record == null || record.getSaleDate() == null) {
                        Log.w(TAG, "Skipping invalid sales record");
                        continue;
                    }

                    // Check date range
                    if (record.getSaleDate().before(startDate) || record.getSaleDate().after(endDate)) {
                        continue;
                    }

                    // Check branch
                    if (selectedBranch != null && !selectedBranch.equals("All Branches") &&
                        !selectedBranch.equals(record.getBranch())) {
                        continue;
                    }

                    filteredRecords.add(record);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing record during filtering", e);
                    // Continue with next record
                }
            }

            Log.d(TAG, "Filtered " + salesRecords.size() + " records down to " + filteredRecords.size());

            // Update adapter
            if (adapter != null) {
                adapter.updateData(filteredRecords);
            } else {
                Log.e(TAG, "Adapter is null, reinitializing");
                adapter = new SalesAdapter(filteredRecords);
                if (salesRecyclerView != null) {
                    salesRecyclerView.setAdapter(adapter);
                }
            }

            // Update summary
            updateSummary(filteredRecords);
        } catch (Exception e) {
            Log.e(TAG, "Error filtering sales data", e);
            Toast.makeText(this,
                "Error filtering sales data: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update the summary information with improved error handling
     *
     * @param records The filtered list of sales records
     */
    private void updateSummary(List<SalesRecord> records) {
        try {
            // Validate input
            if (records == null) {
                Log.e(TAG, "Records list is null in updateSummary");
                records = new ArrayList<>();
            }

            int totalItems = 0;
            double totalAmount = 0;

            for (SalesRecord record : records) {
                try {
                    if (record != null) {
                        totalItems += record.getQuantity();
                        totalAmount += record.getTotalAmount();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing record in summary calculation", e);
                    // Continue with next record
                }
            }

            // Update UI safely
            if (totalItemsText != null) {
                totalItemsText.setText(String.valueOf(totalItems));
            }

            if (totalAmountText != null) {
                totalAmountText.setText(String.format(Locale.getDefault(), "₱%.2f", totalAmount));
            }

            if (recordCountText != null) {
                recordCountText.setText(String.valueOf(records.size()));
            }

            Log.d(TAG, "Summary updated: " + records.size() + " records, " +
                  totalItems + " items, ₱" + totalAmount);
        } catch (Exception e) {
            Log.e(TAG, "Error updating summary", e);
            // Don't show a Toast here to avoid UI clutter
        }
    }

    @Override
    protected void onDestroy() {
        // Unregister the download receiver to prevent memory leaks
        if (downloadReceiver != null) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver not registered
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
        super.onDestroy();
    }

    /**
     * Generate and download a PDF report of sales data
     * This method automatically downloads the report to the Downloads folder
     */
    private void generateReport() {
        if (salesRecords.isEmpty()) {
            Toast.makeText(this, "No sales data to generate report", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Generating report...", Toast.LENGTH_SHORT).show();

        // Generate PDF report in a background thread
        new Thread(() -> {
            try {
                // Generate the report
                File reportFile = PDFReportGenerator.generateSalesReport(
                        this, salesRecords, startDate, endDate, selectedBranch);

                // Process the report on the UI thread
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (reportFile != null) {
                        // Automatically download the report using the new method
                        boolean downloadSuccess = PDFReportGenerator.autoDownloadPdf(this, reportFile);

                        if (downloadSuccess) {
                            // Success! Show success message
                            Toast.makeText(this, "Report downloaded to Downloads folder", Toast.LENGTH_LONG).show();

                            // Open the PDF for viewing
                            PDFReportGenerator.openPdfFile(this, reportFile);
                        } else {
                            // All download methods failed, show error
                            Toast.makeText(this, "Failed to download report. Check app permissions.", Toast.LENGTH_LONG).show();

                            // Still try to open the PDF for viewing from its original location
                            PDFReportGenerator.openPdfFile(this, reportFile);
                        }
                    } else {
                        Toast.makeText(this, "Error generating report", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in report generation thread", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SalesReportActivity.this,
                            "Error generating report: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
