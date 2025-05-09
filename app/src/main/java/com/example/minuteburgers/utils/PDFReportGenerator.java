package com.example.minuteburgers.utils;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.example.minuteburgers.models.SalesRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for generating PDF reports
 */
public class PDFReportGenerator {
    private static final String TAG = "PDFReportGenerator";

    /**
     * Generate a sales report PDF
     *
     * @param context The context
     * @param salesRecords List of sales records
     * @param startDate Start date for the report
     * @param endDate End date for the report
     * @param branch Branch for the report (or null for all branches)
     * @return The generated PDF file
     */
    public static File generateSalesReport(Context context, List<SalesRecord> salesRecords,
                                          Date startDate, Date endDate, String branch) {
        // Create a new PDF document
        PdfDocument document = new PdfDocument();

        // Page info
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();

        // Start a page
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // Paints for drawing
        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(18);
        titlePaint.setFakeBoldText(true);

        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.BLACK);
        headerPaint.setTextSize(14);
        headerPaint.setFakeBoldText(true);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(12);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.GRAY);
        linePaint.setStrokeWidth(1);

        // Date formatter
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        // Draw title
        String title = "Minute Burgers - Sales Report";
        canvas.drawText(title, 50, 50, titlePaint);

        // Draw report period
        String period = "Period: " + dateFormat.format(startDate) + " to " + dateFormat.format(endDate);
        canvas.drawText(period, 50, 80, headerPaint);

        // Draw branch if specified
        if (branch != null && !branch.isEmpty()) {
            canvas.drawText("Branch: " + branch, 50, 100, headerPaint);
        } else {
            canvas.drawText("Branch: All Branches", 50, 100, headerPaint);
        }

        // Draw generation date
        String generatedOn = "Generated on: " + dateFormat.format(new Date());
        canvas.drawText(generatedOn, 50, 120, textPaint);

        // Draw horizontal line
        canvas.drawLine(50, 140, 545, 140, linePaint);

        // Draw table header
        canvas.drawText("Item", 50, 160, headerPaint);
        canvas.drawText("Quantity", 250, 160, headerPaint);
        canvas.drawText("Price", 320, 160, headerPaint);
        canvas.drawText("Total", 390, 160, headerPaint);
        canvas.drawText("Date", 460, 160, headerPaint);

        // Draw horizontal line
        canvas.drawLine(50, 170, 545, 170, linePaint);

        // Draw sales records
        int y = 190;
        double grandTotal = 0;
        int totalQuantity = 0;

        for (SalesRecord record : salesRecords) {
            // Skip records outside the date range
            if (record.getSaleDate().before(startDate) || record.getSaleDate().after(endDate)) {
                continue;
            }

            // Skip records not matching the branch (if specified)
            if (branch != null && !branch.isEmpty() && !record.getBranch().equals(branch)) {
                continue;
            }

            // Draw record
            canvas.drawText(record.getItemName(), 50, y, textPaint);
            canvas.drawText(String.valueOf(record.getQuantity()), 250, y, textPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", record.getUnitPrice()), 320, y, textPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", record.getTotalAmount()), 390, y, textPaint);
            canvas.drawText(dateFormat.format(record.getSaleDate()), 460, y, textPaint);

            // Update totals
            grandTotal += record.getTotalAmount();
            totalQuantity += record.getQuantity();

            // Move to next line
            y += 20;

            // Check if we need a new page
            if (y > 800) {
                // Finish current page
                document.finishPage(page);

                // Start a new page
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }
        }

        // Draw horizontal line
        canvas.drawLine(50, y, 545, y, linePaint);

        // Draw totals
        y += 20;
        canvas.drawText("TOTAL", 50, y, headerPaint);
        canvas.drawText(String.valueOf(totalQuantity), 250, y, headerPaint);
        canvas.drawText("", 320, y, headerPaint);
        canvas.drawText(String.format(Locale.getDefault(), "%.2f", grandTotal), 390, y, headerPaint);

        // Finish the page
        document.finishPage(page);

        // Create file name
        String fileName = "MinuteBurgers_SalesReport_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".pdf";

        // Get the directory for the app's private documents directory
        File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "reports");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file = new File(directory, fileName);

        try {
            // Write the document content to file
            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            document.close();
            fos.close();

            Log.d(TAG, "PDF report generated successfully: " + file.getAbsolutePath());
            return file;
        } catch (IOException e) {
            Log.e(TAG, "Error generating PDF report", e);
            Toast.makeText(context, "Error generating PDF report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Open a PDF file with an external app
     *
     * @param context The context
     * @param file The PDF file to open
     */
    public static void openPdfFile(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get URI for the file using FileProvider
        // Use the correct authority as defined in the manifest
        Uri uri;
        try {
            // First try with .fileprovider (the primary provider)
            uri = FileProvider.getUriForFile(context,
                    context.getApplicationContext().getPackageName() + ".fileprovider", file);
        } catch (IllegalArgumentException e) {
            try {
                // Fallback to .provider (the legacy provider)
                Log.d(TAG, "Falling back to legacy provider");
                uri = FileProvider.getUriForFile(context,
                        context.getApplicationContext().getPackageName() + ".provider", file);
            } catch (IllegalArgumentException e2) {
                // If both fail, log the error and try a direct file URI (less secure)
                Log.e(TAG, "Both FileProvider authorities failed. Using direct file URI", e2);
                uri = Uri.fromFile(file);
            }
        }

        // Create intent to view the PDF
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Check if there's an app that can handle this intent
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            Toast.makeText(context, "No app found to open PDF files", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Download a PDF file to the Downloads directory
     *
     * @param context The context
     * @param file The PDF file to download
     * @return The URI of the downloaded file, or null if download failed
     */
    public static Uri downloadPdfFile(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            String fileName = file.getName();

            // For Android 10 (API 29) and above, use MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                contentValues.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri itemUri = context.getContentResolver().insert(contentUri, contentValues);

                if (itemUri != null) {
                    try (java.io.OutputStream outputStream = context.getContentResolver().openOutputStream(itemUri)) {
                        if (outputStream != null) {
                            java.io.FileInputStream inputStream = new java.io.FileInputStream(file);
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            inputStream.close();

                            contentValues.clear();
                            contentValues.put(MediaStore.Downloads.IS_PENDING, 0);
                            context.getContentResolver().update(itemUri, contentValues, null, null);

                            Log.d(TAG, "PDF downloaded successfully to Downloads: " + fileName);
                            return itemUri;
                        }
                    }
                }
            } else {
                // For older Android versions, use DownloadManager
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File destFile = new File(downloadsDir, fileName);

                // Copy the file to Downloads directory
                java.io.FileInputStream inputStream = new java.io.FileInputStream(file);
                java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                inputStream.close();
                outputStream.close();

                // Notify the system about the new file
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(destFile);
                mediaScanIntent.setData(contentUri);
                context.sendBroadcast(mediaScanIntent);

                Log.d(TAG, "PDF downloaded successfully to Downloads: " + fileName);
                return contentUri;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error downloading PDF file", e);
        }

        return null;
    }

    /**
     * Download a PDF file using direct file copy
     * This is a more reliable method that works across Android versions
     *
     * @param context The context
     * @param file The PDF file to download
     * @return true if download was successful, false otherwise
     */
    public static boolean downloadPdfDirectly(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            String fileName = "MinuteBurgers_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";

            // Create a destination file in the Downloads directory
            File downloadsDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            } else {
                downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }

            if (downloadsDir == null) {
                Log.e(TAG, "Downloads directory is null");
                Toast.makeText(context, "Could not access Downloads directory", Toast.LENGTH_SHORT).show();
                return false;
            }

            // Make sure the directory exists
            if (!downloadsDir.exists()) {
                boolean created = downloadsDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create Downloads directory");
                    Toast.makeText(context, "Failed to create Downloads directory", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            File destFile = new File(downloadsDir, fileName);

            // Copy the file
            try (FileInputStream in = new FileInputStream(file);
                 FileOutputStream out = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            Log.d(TAG, "PDF saved successfully to: " + destFile.getAbsolutePath());

            // Make the file visible in the Downloads app
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, use MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.IS_PENDING, 0);

                ContentResolver resolver = context.getContentResolver();
                Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                resolver.insert(contentUri, values);
            } else {
                // For older versions, use media scanner
                MediaScannerConnection.scanFile(context,
                        new String[]{destFile.getAbsolutePath()},
                        new String[]{"application/pdf"},
                        null);
            }

            // Show success message
            Toast.makeText(context, "Report saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving PDF file", e);
            Toast.makeText(context, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Download a PDF file using DownloadManager
     * This is a backup method that uses Android's DownloadManager
     *
     * @param context The context
     * @param file The PDF file to download
     * @return The download ID, or -1 if download failed
     */
    public static long downloadPdfWithDownloadManager(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show();
            return -1;
        }

        try {
            // Get URI for the file using FileProvider
            Uri uri = FileProvider.getUriForFile(context,
                    context.getApplicationContext().getPackageName() + ".fileprovider", file);

            String fileName = "MinuteBurgers_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";

            // Create download request
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Minute Burgers Sales Report");
            request.setDescription("Downloading sales report PDF");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setMimeType("application/pdf");

            // Add necessary flags
            request.addRequestHeader("Content-Type", "application/pdf");
            request.allowScanningByMediaScanner();

            // Get download service and enqueue the request
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                long downloadId = downloadManager.enqueue(request);
                Log.d(TAG, "PDF download started with ID: " + downloadId);
                return downloadId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting PDF download with DownloadManager", e);
        }

        return -1;
    }

    /**
     * Automatically download any generated PDF file
     * This method tries multiple download methods to ensure the PDF is saved to the Downloads folder
     *
     * @param context The context
     * @param file The PDF file to download
     * @return true if any download method succeeded, false if all methods failed
     */
    public static boolean autoDownloadPdf(Context context, File file) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "Cannot auto-download: PDF file not found or doesn't exist");
            return false;
        }

        Log.d(TAG, "Auto-downloading PDF: " + file.getAbsolutePath());

        // Try direct download first (most reliable)
        boolean directDownloadSuccess = downloadPdfDirectly(context, file);
        if (directDownloadSuccess) {
            Log.d(TAG, "Auto-download successful using direct method");
            return true;
        }

        // If direct download fails, try DownloadManager
        Log.d(TAG, "Direct download failed, trying DownloadManager...");
        long downloadId = downloadPdfWithDownloadManager(context, file);
        if (downloadId != -1) {
            Log.d(TAG, "Auto-download initiated using DownloadManager");
            return true;
        }

        // If DownloadManager fails, try the legacy method
        Log.d(TAG, "DownloadManager failed, trying legacy download method...");
        Uri downloadUri = downloadPdfFile(context, file);
        if (downloadUri != null) {
            Log.d(TAG, "Auto-download successful using legacy method");
            return true;
        }

        // All methods failed
        Log.e(TAG, "All auto-download methods failed");
        return false;
    }
}
