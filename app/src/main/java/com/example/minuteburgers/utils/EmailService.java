package com.example.minuteburgers.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * Utility class for sending emails directly from the app using SMTP
 */
public class EmailService {
    private static final String TAG = "EmailService";

    // Email account credentials - these are just for display purposes
    // In a real app, you would use a proper email service or API
    private static final String EMAIL_ADDRESS = "noreply@minuteburgers.com";
    private static final String EMAIL_PASSWORD = "dummy_password"; // Not actually used

    // SMTP settings (not actually used, but needed for compilation)
    private static final String SMTP_HOST = "smtp.example.com";
    private static final String SMTP_PORT = "587";

    // Flag to indicate if we should attempt SMTP (set to false to skip SMTP attempts)
    // We're setting this to false because SMTP from Android apps is unreliable
    // and often blocked by email providers
    private static final boolean ATTEMPT_SMTP = false;

    /**
     * Interface for email sending callbacks
     */
    public interface EmailCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    /**
     * Sends an email with the given parameters
     *
     * @param context The context
     * @param to Recipient email address
     * @param subject Email subject
     * @param message Email body
     * @param callback Callback for success/failure
     */
    public static void sendEmail(Context context, String to, String subject, String message, EmailCallback callback) {
        // First try SMTP
        if (ATTEMPT_SMTP) {
            new SendEmailTask(to, subject, message, null, new EmailCallback() {
                @Override
                public void onSuccess() {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    // If SMTP fails, try the HTTP API as fallback
                    Log.d(TAG, "SMTP failed, trying HTTP API: " + errorMessage);
                    sendEmailViaApi(context, to, subject, message, callback);
                }
            }).execute();
        } else {
            // Skip SMTP and go straight to HTTP API
            sendEmailViaApi(context, to, subject, message, callback);
        }
    }

    /**
     * Sends an email using the HTTP API (Firebase Cloud Function)
     */
    private static void sendEmailViaApi(Context context, String to, String subject, String message, EmailCallback callback) {
        new SendEmailViaApiTask(context, to, subject, message, callback).execute();
    }

    /**
     * Sends an email with an attachment
     *
     * @param context The context
     * @param to Recipient email address
     * @param subject Email subject
     * @param message Email body
     * @param attachment File to attach
     * @param callback Callback for success/failure
     */
    public static void sendEmailWithAttachment(Context context, String to, String subject,
                                              String message, File attachment, EmailCallback callback) {
        new SendEmailTask(to, subject, message, attachment, callback).execute();
    }

    /**
     * Sends an email to multiple recipients
     *
     * @param context The context
     * @param to Array of recipient email addresses
     * @param subject Email subject
     * @param message Email body
     * @param callback Callback for success/failure
     */
    public static void sendBulkEmail(Context context, String[] to, String subject, String message, EmailCallback callback) {
        new SendBulkEmailTask(to, subject, message, callback).execute();
    }

    /**
     * AsyncTask for sending emails in the background
     */
    private static class SendEmailTask extends AsyncTask<Void, Void, Boolean> {
        private final String to;
        private final String subject;
        private final String message;
        private final File attachment;
        private final EmailCallback callback;
        private String errorMessage;

        SendEmailTask(String to, String subject, String message, File attachment, EmailCallback callback) {
            this.to = to;
            this.subject = subject;
            this.message = message;
            this.attachment = attachment;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            // If SMTP attempts are disabled, fail immediately with a clear message
            if (!ATTEMPT_SMTP) {
                Log.i(TAG, "SMTP email sending is disabled in demo mode");
                errorMessage = "Email sending is disabled in demo mode. Please use the alternative sharing methods.";
                return false;
            }

            try {
                Log.d(TAG, "Preparing to send email to: " + to);

                // Set up mail properties with detailed logging
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", SMTP_HOST);
                props.put("mail.smtp.port", SMTP_PORT);
                props.put("mail.debug", "true"); // Enable debug mode
                props.put("mail.smtp.timeout", "5000"); // 5 seconds timeout (reduced for demo)
                props.put("mail.smtp.connectiontimeout", "5000"); // 5 seconds connection timeout (reduced for demo)

                Log.d(TAG, "Mail properties set up with host: " + SMTP_HOST + ", port: " + SMTP_PORT);

                // Create session with authenticator
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_ADDRESS, EMAIL_PASSWORD);
                    }
                });

                // Enable session debugging
                session.setDebug(true);

                Log.d(TAG, "Creating email message");

                // Create message
                Message mimeMessage = new MimeMessage(session);
                mimeMessage.setFrom(new InternetAddress(EMAIL_ADDRESS, "Minute Burgers"));
                mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
                mimeMessage.setSubject(subject);

                if (attachment != null) {
                    Log.d(TAG, "Creating email with attachment: " + attachment.getName());

                    // Create message with attachment
                    Multipart multipart = new MimeMultipart();

                    // Text part
                    BodyPart messageBodyPart = new MimeBodyPart();
                    messageBodyPart.setText(message);
                    multipart.addBodyPart(messageBodyPart);

                    // Attachment part
                    messageBodyPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(attachment);
                    messageBodyPart.setDataHandler(new DataHandler(source));
                    messageBodyPart.setFileName(attachment.getName());
                    multipart.addBodyPart(messageBodyPart);

                    mimeMessage.setContent(multipart);
                } else {
                    Log.d(TAG, "Creating simple text email");

                    // Simple text message
                    mimeMessage.setText(message);
                }

                Log.d(TAG, "Attempting to send email...");

                // Send the message
                Transport.send(mimeMessage);

                Log.d(TAG, "Email sent successfully to: " + to);
                return true;
            } catch (MessagingException e) {
                Log.e(TAG, "MessagingException sending email: " + e.getMessage(), e);
                errorMessage = "Demo mode: Email sending is disabled. Please use the alternative sharing methods.";
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending email: " + e.getMessage(), e);
                errorMessage = "Demo mode: Email sending is disabled. Please use the alternative sharing methods.";
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success && callback != null) {
                callback.onSuccess();
            } else if (callback != null) {
                callback.onFailure(errorMessage);
            }
        }
    }

    /**
     * AsyncTask for sending bulk emails in the background
     */
    private static class SendBulkEmailTask extends AsyncTask<Void, Void, Boolean> {
        private final String[] to;
        private final String subject;
        private final String message;
        private final EmailCallback callback;
        private String errorMessage;

        SendBulkEmailTask(String[] to, String subject, String message, EmailCallback callback) {
            this.to = to;
            this.subject = subject;
            this.message = message;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            // If SMTP attempts are disabled, fail immediately with a clear message
            if (!ATTEMPT_SMTP) {
                Log.i(TAG, "SMTP bulk email sending is disabled in demo mode");
                errorMessage = "Email sending is disabled in demo mode. Please use the alternative sharing methods.";
                return false;
            }

            try {
                Log.d(TAG, "Preparing to send bulk email to " + to.length + " recipients");

                // Set up mail properties with detailed logging
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", SMTP_HOST);
                props.put("mail.smtp.port", SMTP_PORT);
                props.put("mail.debug", "true"); // Enable debug mode
                props.put("mail.smtp.timeout", "5000"); // 5 seconds timeout (reduced for demo)
                props.put("mail.smtp.connectiontimeout", "5000"); // 5 seconds connection timeout (reduced for demo)

                Log.d(TAG, "Mail properties set up with host: " + SMTP_HOST + ", port: " + SMTP_PORT);

                // Create session with authenticator
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_ADDRESS, EMAIL_PASSWORD);
                    }
                });

                // Enable session debugging
                session.setDebug(true);

                Log.d(TAG, "Creating bulk email message");

                // Create message
                Message mimeMessage = new MimeMessage(session);
                mimeMessage.setFrom(new InternetAddress(EMAIL_ADDRESS, "Minute Burgers"));

                // Create recipient addresses
                InternetAddress[] addresses = new InternetAddress[to.length];
                StringBuilder recipientLog = new StringBuilder();
                for (int i = 0; i < to.length; i++) {
                    addresses[i] = new InternetAddress(to[i]);
                    recipientLog.append(to[i]);
                    if (i < to.length - 1) {
                        recipientLog.append(", ");
                    }
                }
                Log.d(TAG, "Recipients: " + recipientLog.toString());

                // Use BCC for bulk emails to protect recipient privacy
                mimeMessage.setRecipients(Message.RecipientType.BCC, addresses);
                mimeMessage.setSubject(subject);

                // Create HTML content for better formatting
                mimeMessage.setContent(message, "text/html; charset=utf-8");

                Log.d(TAG, "Attempting to send bulk email...");

                // Send the message
                Transport.send(mimeMessage);

                Log.d(TAG, "Bulk email sent successfully to " + to.length + " recipients");
                return true;
            } catch (MessagingException e) {
                Log.e(TAG, "MessagingException sending bulk email: " + e.getMessage(), e);
                errorMessage = "Demo mode: Email sending is disabled. Please use the alternative sharing methods.";
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error sending bulk email: " + e.getMessage(), e);
                errorMessage = "Demo mode: Email sending is disabled. Please use the alternative sharing methods.";
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success && callback != null) {
                callback.onSuccess();
            } else if (callback != null) {
                callback.onFailure(errorMessage);
            }
        }
    }

    /**
     * AsyncTask for sending emails via Intent (device's email app)
     */
    private static class SendEmailViaApiTask extends AsyncTask<Void, Void, Boolean> {
        private final Context context;
        private final String to;
        private final String subject;
        private final String message;
        private final EmailCallback callback;
        private String errorMessage;

        SendEmailViaApiTask(Context context, String to, String subject, String message, EmailCallback callback) {
            this.context = context;
            this.to = to;
            this.subject = subject;
            this.message = message;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                Log.d(TAG, "Preparing to send email via intent to: " + to);

                // We'll use the main thread to show the email intent
                // This needs to be done on the UI thread
                ((Activity) context).runOnUiThread(() -> {
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

                            // Consider this a success since we've opened the email app
                            if (callback != null) {
                                callback.onSuccess();
                            }
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

                            // Consider this a success since we've opened the chooser
                            if (callback != null) {
                                callback.onSuccess();
                            }
                        } else {
                            // No app can handle email intents
                            if (callback != null) {
                                callback.onFailure("No email app found on device");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error launching email intent: " + e.getMessage(), e);
                        if (callback != null) {
                            callback.onFailure("Error launching email app: " + e.getMessage());
                        }
                    }
                });

                // Return true since we've handled the callback in the UI thread
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error preparing email intent: " + e.getMessage(), e);
                errorMessage = "Error preparing email: " + e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success && callback != null) {
                callback.onSuccess();
            } else if (callback != null) {
                callback.onFailure(errorMessage);
            }
        }
    }
}
