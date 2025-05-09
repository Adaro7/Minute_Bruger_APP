package com.example.minuteburgers.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.minuteburgers.utils.EmailService;

import com.example.minuteburgers.models.StockRequest;
import com.example.minuteburgers.models.User;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EmailSender {
    private static final String TAG = "EmailSender";

    /**
     * Sends a generic email using the device's email client
     *
     * @param context The context
     * @param to The recipient email address
     * @param subject The email subject
     * @param message The email message
     */
    public static void sendEmail(Context context, String to, String subject, String message) {
        // Use the EmailService to send the email
        EmailService.sendEmail(context, to, subject, message, new EmailService.EmailCallback() {
            @Override
            public void onSuccess() {
                // Show success message
                Toast.makeText(context,
                    "Email sent to " + to + " from minuteburger@email.com",
                    Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                // Show error message
                Toast.makeText(context,
                    "Failed to send email: " + errorMessage,
                    Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error sending email: " + errorMessage);

                // Fallback to the old method
                try {
                    // First try with ACTION_SENDTO (specific to email apps)
                    Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                    emailIntent.setData(Uri.parse("mailto:")); // only email apps should handle this
                    emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                    emailIntent.putExtra(Intent.EXTRA_TEXT, message);

                    // Check if there's an app that can handle this intent
                    if (emailIntent.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(emailIntent);
                        Toast.makeText(context, "Opening email app...", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Fallback to ACTION_SEND (more general, can be handled by more apps)
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, message);

                    if (sendIntent.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(Intent.createChooser(sendIntent, "Send email via:"));
                    } else {
                        // Second fallback: Show a dialog with the email details
                        List<String> recipients = new ArrayList<>();
                        recipients.add(to);
                        showAnnouncementDetailsDialog(context, recipients, subject, message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Fallback email sending also failed", e);

                    // Show dialog with email details as a fallback
                    List<String> recipients = new ArrayList<>();
                    recipients.add(to);
                    showAnnouncementDetailsDialog(context, recipients, subject, message);
                }
            }
        });
    }

    public static void sendStockRequestEmail(Context context, StockRequest request, String ownerEmail) {
        // Define these variables outside the try block so they're accessible in the catch block
        String subject = "Stock Request: " + request.getItemName();
        StringBuilder body = new StringBuilder();

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

            body.append("Stock Request Details:\n\n");
            body.append("Item: ").append(request.getItemName()).append("\n");
            body.append("Quantity: ").append(request.getQuantity()).append("\n");
            body.append("Requested By: ").append(request.getRequestedByName()).append(" (").append(request.getRequestedByEmail()).append(")\n");
            body.append("Branch: ").append(request.getBranch()).append("\n");
            body.append("Date: ").append(dateFormat.format(request.getRequestDate())).append("\n");

            if (request.getNotes() != null && !request.getNotes().isEmpty()) {
                body.append("\nNotes: ").append(request.getNotes()).append("\n");
            }

            body.append("\nPlease log in to the Minute Burgers app to approve or reject this request.");
            body.append("\n\nThis email was sent automatically from minuteburger@email.com");

            // Show toast message
            Toast.makeText(context,
                "Sending stock request to owner from minuteburger@email.com",
                Toast.LENGTH_SHORT).show();

            // Use the EmailService to send the email
            EmailService.sendEmail(context, ownerEmail, subject, body.toString(), new EmailService.EmailCallback() {
                @Override
                public void onSuccess() {
                    // Show success message
                    Toast.makeText(context,
                        "Stock request sent to owner from minuteburger@email.com",
                        Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(String errorMessage) {
                    // Show error message
                    Toast.makeText(context,
                        "Failed to send stock request: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error sending stock request: " + errorMessage);

                    // Fallback to the old method
                    try {
                        // First try with ACTION_SENDTO (specific to email apps)
                        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                        emailIntent.setData(Uri.parse("mailto:")); // only email apps should handle this
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{ownerEmail});
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                        emailIntent.putExtra(Intent.EXTRA_TEXT, body.toString());

                        // Check if there's an app that can handle this intent
                        if (emailIntent.resolveActivity(context.getPackageManager()) != null) {
                            context.startActivity(emailIntent);
                            Toast.makeText(context, "Opening email app to send stock request", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Fallback to ACTION_SEND (more general, can be handled by more apps)
                        Intent sendIntent = new Intent(Intent.ACTION_SEND);
                        sendIntent.setType("text/plain");
                        sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{ownerEmail});
                        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, body.toString());

                        if (sendIntent.resolveActivity(context.getPackageManager()) != null) {
                            context.startActivity(Intent.createChooser(sendIntent, "Send stock request via:"));
                        } else {
                            // Second fallback: Show a dialog with the stock request details
                            List<String> recipients = new ArrayList<>();
                            recipients.add(ownerEmail);
                            showAnnouncementDetailsDialog(context, recipients, subject, body.toString());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Fallback email sending also failed", e);

                        // Show dialog with stock request details as a fallback
                        List<String> recipients = new ArrayList<>();
                        recipients.add(ownerEmail);
                        showAnnouncementDetailsDialog(context, recipients, subject, body.toString());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error preparing email", e);
            Toast.makeText(context, "Error preparing stock request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void findOwnerEmailAndSendRequest(Context context, StockRequest request) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "owner")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<String> ownerEmails = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String email = document.getString("email");
                    if (email != null && !email.isEmpty()) {
                        ownerEmails.add(email);
                    }
                }

                if (!ownerEmails.isEmpty()) {
                    // Send to the first owner found
                    sendStockRequestEmail(context, request, ownerEmails.get(0));
                } else {
                    Log.e(TAG, "No owner email found");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding owner email", e);
            });
    }

    /**
     * Sends an announcement to all employees via email
     *
     * @param context The context
     * @param title The announcement title
     * @param content The announcement content
     */
    public static void sendAnnouncementToEmployees(Context context, String title, String content) {
        // Show initial progress dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(context)
            .setTitle("Preparing Announcement")
            .setMessage("Finding employee email addresses...")
            .setCancelable(false)
            .create();
        loadingDialog.show();

        // Get all employees from Firestore
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "employee")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Dismiss the loading dialog
                loadingDialog.dismiss();

                List<String> employeeEmails = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String email = document.getString("email");
                    if (email != null && !email.isEmpty()) {
                        employeeEmails.add(email);
                    }
                }

                if (!employeeEmails.isEmpty()) {
                    // Log the emails found
                    Log.d(TAG, "Found " + employeeEmails.size() + " employee emails: " + employeeEmails);

                    // Create email content
                    String subject = "Minute Burgers Announcement: " + title;
                    StringBuilder body = new StringBuilder();
                    body.append("ANNOUNCEMENT\n\n");
                    body.append(title).append("\n\n");
                    body.append(content).append("\n\n");
                    body.append("Date: ").append(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new java.util.Date())).append("\n\n");
                    body.append("This is an automated message from the Minute Burgers Management System.");

                    // Send to all employees
                    sendBulkEmail(context, employeeEmails, subject, body.toString());
                } else {
                    Log.e(TAG, "No employee emails found");
                    Toast.makeText(context, "No employees found to send announcement", Toast.LENGTH_SHORT).show();

                    // Show a dialog explaining the issue
                    new AlertDialog.Builder(context)
                        .setTitle("No Employees Found")
                        .setMessage("No employee email addresses were found in the database. Please make sure employees have been added to the system.")
                        .setPositiveButton("OK", null)
                        .show();
                }
            })
            .addOnFailureListener(e -> {
                // Dismiss the loading dialog
                loadingDialog.dismiss();

                Log.e(TAG, "Error finding employee emails", e);
                Toast.makeText(context, "Error finding employees: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                // Show a dialog explaining the error
                new AlertDialog.Builder(context)
                    .setTitle("Error Finding Employees")
                    .setMessage("There was an error finding employee email addresses: " + e.getMessage() +
                                "\n\nPlease try again later.")
                    .setPositiveButton("OK", null)
                    .show();
            });
    }

    /**
     * Sends an email to multiple recipients
     *
     * @param context The context
     * @param recipients List of recipient email addresses
     * @param subject The email subject
     * @param message The email message
     */
    private static void sendBulkEmail(Context context, List<String> recipients, String subject, String message) {
        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(context)
            .setTitle("Sending Announcement")
            .setMessage("Preparing to send announcement to " + recipients.size() + " employees...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        // Convert list to array
        String[] recipientsArray = recipients.toArray(new String[0]);

        try {
            // Directly use the intent-based approach instead of EmailService
            // First try with ACTION_SENDTO (specific to email apps)
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:")); // only email apps should handle this
            emailIntent.putExtra(Intent.EXTRA_EMAIL, recipientsArray);
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, message);

            // Dismiss progress dialog
            progressDialog.dismiss();

            // Check if there's an app that can handle this intent
            if (emailIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(emailIntent);
                Toast.makeText(context, "Opening email app to send announcement", Toast.LENGTH_LONG).show();
                return;
            }

            // Fallback to ACTION_SEND (more general, can be handled by more apps)
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_EMAIL, recipientsArray);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            sendIntent.putExtra(Intent.EXTRA_TEXT, message);

            if (sendIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(Intent.createChooser(sendIntent, "Send announcement via:"));
                Toast.makeText(context, "Choose an app to send the announcement", Toast.LENGTH_LONG).show();
            } else {
                // Second fallback: Show a dialog with the announcement details
                showAnnouncementDetailsDialog(context, recipients, subject, message);
                Toast.makeText(context, "No email app found. Showing announcement details.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            // Dismiss progress dialog
            progressDialog.dismiss();

            Log.e(TAG, "Error sending announcement", e);
            Toast.makeText(context, "Error sending announcement: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // Show dialog with announcement details as a fallback
            showAnnouncementDetailsDialog(context, recipients, subject, message);
        }
    }

    /**
     * Shows a dialog with announcement details when email sending fails
     *
     * @param context The context
     * @param recipients List of recipient email addresses
     * @param subject The email subject
     * @param message The email message
     */
    private static void showAnnouncementDetailsDialog(Context context, List<String> recipients, String subject, String message) {
        // Build a string with all recipient emails
        StringBuilder emailsBuilder = new StringBuilder();
        for (String email : recipients) {
            emailsBuilder.append(email).append("\n");
        }

        // Create and show an alert dialog with the announcement details
        new AlertDialog.Builder(context)
            .setTitle("Announcement Details")
            .setMessage("No email app was found, but the announcement has been saved in the system. " +
                    "Here are the details:\n\n" +
                    "Subject: " + subject + "\n\n" +
                    "Message: " + message + "\n\n" +
                    "Recipients (" + recipients.size() + "):\n" + emailsBuilder.toString())
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy Details", (dialog, which) -> {
                // Copy announcement details to clipboard
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Announcement Details",
                        "Subject: " + subject + "\n\n" +
                        "Message: " + message + "\n\n" +
                        "Recipients: " + recipients.toString());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Announcement details copied to clipboard", Toast.LENGTH_SHORT).show();
            })
            .show();
    }
}
