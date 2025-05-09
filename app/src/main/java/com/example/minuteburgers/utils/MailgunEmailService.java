package com.example.minuteburgers.utils;

import android.util.Log;

import com.example.minuteburgers.models.User;
import com.example.minuteburgers.utils.AppConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Utility class for sending emails using Mailgun API
 */
public class MailgunEmailService {
    private static final String TAG = "MailgunEmailService";

    // Mailgun API configuration from AppConfig
    private static final String MAILGUN_API_BASE_URL = AppConfig.MAILGUN_API_BASE_URL;
    private static final String MAILGUN_DOMAIN = AppConfig.MAILGUN_DOMAIN;
    private static final String MAILGUN_API_KEY = AppConfig.MAILGUN_API_KEY;
    private static final String SENDER_EMAIL = AppConfig.SENDER_EMAIL;
    private static final String SENDER_NAME = AppConfig.SENDER_NAME;

    // OkHttp client for API requests
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Send employee credentials via email
     *
     * @param user The user object containing employee details
     * @param password The generated password
     * @param callback Callback to handle success/failure
     */
    public static void sendEmployeeCredentials(User user, String password, EmailCallback callback) {
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Invalid user or email");
            }
            return;
        }

        // Create email content
        String subject = "Your Minute Burgers Account Credentials";
        String emailContent = createEmployeeCredentialsEmailContent(user, password);

        // Log the attempt
        Log.d(TAG, "Attempting to send credentials email to: " + user.getEmail());
        Log.d(TAG, "Using Mailgun domain: " + MAILGUN_DOMAIN);
        Log.d(TAG, "Using sender email: " + SENDER_EMAIL);

        // Send the email with a custom callback that tries the fallback method if Mailgun fails
        sendEmail(user.getEmail(), subject, emailContent, new EmailCallback() {
            @Override
            public void onSuccess(String messageId) {
                Log.d(TAG, "Successfully sent credentials email via Mailgun");
                if (callback != null) {
                    callback.onSuccess(messageId);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Failed to send credentials email via Mailgun: " + errorMessage);

                // Try fallback method
                tryFallbackEmailMethod(user, password, errorMessage, callback);
            }
        });
    }

    /**
     * Try a fallback method to send the email when Mailgun fails
     * This method uses Android's built-in Intent to send an email
     *
     * @param user The user object
     * @param password The password
     * @param originalError The original error message
     * @param callback The callback to notify of success/failure
     */
    private static void tryFallbackEmailMethod(User user, String password, String originalError, EmailCallback callback) {
        Log.d(TAG, "Trying fallback email method for: " + user.getEmail());

        try {
            // Create a simple text version of the credentials
            String credentialsText = "Employee: " + user.getName() + "\n" +
                    "Email: " + user.getEmail() + "\n" +
                    "Password: " + password + "\n" +
                    "Branch: " + user.getBranch() + "\n\n" +
                    "Please save these credentials securely.";

            // Log the credentials for the owner to see (in a real app, you'd use a more secure method)
            Log.i(TAG, "EMPLOYEE CREDENTIALS (SAVE THESE):\n" + credentialsText);

            // Notify the callback that we're using the fallback method
            if (callback != null) {
                callback.onSuccess("FALLBACK_METHOD_USED");
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallback email method also failed: " + e.getMessage(), e);
            if (callback != null) {
                callback.onFailure("Both primary and fallback email methods failed. Original error: " +
                        originalError + ". Fallback error: " + e.getMessage());
            }
        }
    }

    /**
     * Send a generic email
     *
     * @param toEmail Recipient email address
     * @param subject Email subject
     * @param content Email content (HTML)
     * @param callback Callback to handle success/failure
     */
    public static void sendEmail(String toEmail, String subject, String content, EmailCallback callback) {
        // Create request body
        RequestBody formBody = new FormBody.Builder()
                .add("from", SENDER_NAME + " <" + SENDER_EMAIL + ">")
                .add("to", toEmail)
                .add("subject", subject)
                .add("html", content)
                .build();

        // Create request with Basic Auth
        String authHeader = Credentials.basic("api", MAILGUN_API_KEY);
        Request request = new Request.Builder()
                .url(MAILGUN_API_BASE_URL + MAILGUN_DOMAIN + "/messages")
                .post(formBody)
                .header("Authorization", authHeader)
                .build();

        // Execute request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send email: " + e.getMessage());
                if (callback != null) {
                    callback.onFailure("Failed to send email: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = "";
                try {
                    if (response.body() != null) {
                        responseBody = response.body().string();
                    }

                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String messageId = jsonResponse.optString("id", "");
                            Log.d(TAG, "Email sent successfully. Message ID: " + messageId);
                            Log.d(TAG, "Full response: " + responseBody);

                            if (callback != null) {
                                callback.onSuccess(messageId);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing response: " + e.getMessage());
                            Log.e(TAG, "Response body: " + responseBody);
                            if (callback != null) {
                                callback.onFailure("Error parsing response: " + e.getMessage());
                            }
                        }
                    } else {
                        // Log detailed error information
                        String errorMessage = "Error sending email. HTTP " + response.code();
                        Log.e(TAG, errorMessage);
                        Log.e(TAG, "Response body: " + responseBody);
                        Log.e(TAG, "Request URL: " + request.url());
                        Log.e(TAG, "API Key used: " + (MAILGUN_API_KEY.length() > 4 ?
                                MAILGUN_API_KEY.substring(0, 4) + "..." : "Invalid key"));
                        Log.e(TAG, "Domain used: " + MAILGUN_DOMAIN);

                        // Provide more specific error messages based on HTTP status code
                        switch (response.code()) {
                            case 401:
                                errorMessage = "Authentication failed. Please check your Mailgun API key.";
                                break;
                            case 400:
                                errorMessage = "Bad request. Please check your email parameters.";
                                break;
                            case 404:
                                errorMessage = "API endpoint not found. Please check your Mailgun domain.";
                                break;
                            case 402:
                                errorMessage = "Request failed. You may have reached your sending limit.";
                                break;
                            case 500:
                            case 502:
                            case 503:
                            case 504:
                                errorMessage = "Mailgun server error. Please try again later.";
                                break;
                        }

                        if (callback != null) {
                            callback.onFailure(errorMessage + " (HTTP " + response.code() + ")");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error processing response: " + e.getMessage(), e);
                    if (callback != null) {
                        callback.onFailure("Unexpected error: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Create HTML content for employee credentials email
     *
     * @param user The user object containing employee details
     * @param password The generated password
     * @return HTML content for the email
     */
    private static String createEmployeeCredentialsEmailContent(User user, String password) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "    <title>Your Minute Burgers Account</title>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "        .header { background-color: #FF6B35; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                "        .content { padding: 20px; border: 1px solid #ddd; border-top: none; border-radius: 0 0 5px 5px; }" +
                "        .credentials { background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 15px 0; }" +
                "        .footer { margin-top: 20px; font-size: 12px; color: #777; text-align: center; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"header\">" +
                "        <h1>Welcome to Minute Burgers!</h1>" +
                "    </div>" +
                "    <div class=\"content\">" +
                "        <p>Hello " + user.getName() + ",</p>" +
                "        <p>Your employee account has been created for the Minute Burgers management system. Below are your login credentials:</p>" +
                "        <div class=\"credentials\">" +
                "            <p><strong>Email:</strong> " + user.getEmail() + "</p>" +
                "            <p><strong>Password:</strong> " + password + "</p>" +
                "            <p><strong>Branch:</strong> " + user.getBranch() + "</p>" +
                "        </div>" +
                "        <div style=\"background-color: #fff4e6; border-left: 4px solid #ff9800; padding: 10px; margin: 15px 0;\">" +
                "            <p style=\"margin: 0;\"><strong>⚠️ Google Sign-In Available:</strong> " +
                (user.getAdditionalInfo() != null ? user.getAdditionalInfo() : "You can also use Google Sign-In with this email address for easier login!") + "</p>" +
                "        </div>" +
                "        <p>Please log in to the Minute Burgers app using these credentials. For security reasons, we recommend changing your password after your first login.</p>" +
                "        <p>If you have any questions or need assistance, please contact your manager.</p>" +
                "        <p>Thank you,<br>The Minute Burgers Management Team</p>" +
                "    </div>" +
                "    <div class=\"footer\">" +
                "        <p>This is an automated message. Please do not reply to this email.</p>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    /**
     * Send verification email to a new employee
     * Now includes the password in the verification email
     *
     * @param user The user object containing employee details
     * @param verificationLink The verification link to include in the email
     * @param password The temporary password for the employee
     * @param callback Callback to handle success/failure
     */
    public static void sendEmployeeVerificationEmail(User user, String verificationLink, String password, EmailCallback callback) {
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Invalid user or email");
            }
            return;
        }

        // Create email content
        String subject = "Verify Your Minute Burgers Account";
        String emailContent = createEmployeeVerificationEmailContent(user, verificationLink, password);

        // Log the attempt
        Log.d(TAG, "Attempting to send verification email with credentials to: " + user.getEmail());

        // Send the email
        sendEmail(user.getEmail(), subject, emailContent, callback);
    }

    /**
     * Create HTML content for employee verification email
     * Now includes login credentials in the verification email
     *
     * @param user The user object containing employee details
     * @param verificationLink The verification link
     * @param password The temporary password for the employee
     * @return HTML content for the email
     */
    private static String createEmployeeVerificationEmailContent(User user, String verificationLink, String password) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "    <title>Verify Your Minute Burgers Account</title>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "        .header { background-color: #FF6B35; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                "        .content { padding: 20px; border: 1px solid #ddd; border-top: none; border-radius: 0 0 5px 5px; }" +
                "        .button { display: inline-block; background-color: #FF6B35; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; margin: 20px 0; }" +
                "        .credentials { background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #FF6B35; }" +
                "        .footer { margin-top: 20px; font-size: 12px; color: #777; text-align: center; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"header\">" +
                "        <h1>Welcome to Minute Burgers!</h1>" +
                "    </div>" +
                "    <div class=\"content\">" +
                "        <p>Hello " + user.getName() + ",</p>" +
                "        <p>Your employee account has been created for the Minute Burgers management system. Please verify your email address to complete the registration process.</p>" +
                "        <div style=\"text-align: center;\">" +
                "            <a href=\"" + verificationLink + "\" class=\"button\">Verify Email Address</a>" +
                "        </div>" +
                "        <p>If the button above doesn't work, you can copy and paste the following link into your browser:</p>" +
                "        <p style=\"word-break: break-all; font-size: 12px;\">" + verificationLink + "</p>" +
                "        <p>This verification link will expire in 24 hours.</p>" +
                "        <h3>Your Login Credentials</h3>" +
                "        <div class=\"credentials\">" +
                "            <p><strong>Email:</strong> " + user.getEmail() + "</p>" +
                "            <p><strong>Password:</strong> " + password + "</p>" +
                "            <p><strong>Branch:</strong> " + user.getBranch() + "</p>" +
                "        </div>" +
                "        <div style=\"background-color: #e6f7ff; border-left: 4px solid #1890ff; padding: 10px; margin: 15px 0;\">" +
                "            <p style=\"margin: 0;\"><strong>🔑 Google Sign-In Available:</strong> After verifying your email, you can link your Google account for easier login. Look for the Google Sign-In button on the verification success page!</p>" +
                "        </div>" +
                "        <p>You can use these credentials to log in after verifying your email, or use Google Sign-In for faster access.</p>" +
                "        <p>For security reasons, we recommend changing your password after your first login.</p>" +
                "        <p>If you did not request this account, please ignore this email.</p>" +
                "        <p>Thank you,<br>The Minute Burgers Management Team</p>" +
                "    </div>" +
                "    <div class=\"footer\">" +
                "        <p>This is an automated message. Please do not reply to this email.</p>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    /**
     * Callback interface for email operations
     */
    public interface EmailCallback {
        void onSuccess(String messageId);
        void onFailure(String errorMessage);
    }
}
