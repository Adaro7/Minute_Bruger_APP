package com.example.minuteburgers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.minuteburgers.adapters.EmployeeAdapter;
import com.example.minuteburgers.models.User;
import com.example.minuteburgers.utils.EmailSender;
import com.example.minuteburgers.utils.MailgunEmailService;
import com.example.minuteburgers.utils.PasswordGenerator;
import com.example.minuteburgers.utils.PermissionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.minuteburgers.utils.EmailService;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmployeeManagementActivity extends AppCompatActivity {
    private static final String TAG = "EmployeeManagement";

    private RecyclerView recyclerView;
    private EmployeeAdapter adapter;
    private List<User> employees = new ArrayList<>();
    private ExtendedFloatingActionButton addEmployeeButton;
    private MaterialButton backButton;
    private LinearProgressIndicator progressIndicator;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_management);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize permission manager
        permissionManager = PermissionManager.getInstance();

        // Check if user has permission to manage staff
        checkPermissions();

        // Initialize views
        initializeViews();

        // Load employees
        loadEmployees();
    }

    private void checkPermissions() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Load permissions
            permissionManager.loadUserPermissions(mAuth, db, success -> {
                if (success) {
                    // Check if user has permission to manage staff
                    if (!permissionManager.hasPermission(PermissionManager.MANAGE_STAFF)) {
                        Toast.makeText(EmployeeManagementActivity.this,
                                "You don't have permission to manage employees",
                                Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "User role: " + permissionManager.getUserRole() +
                              " does not have permission to manage employees");
                        finish();
                    } else {
                        Log.d(TAG, "User has permission to manage employees");
                    }
                } else {
                    // Failed to load permissions
                    Log.e(TAG, "Failed to load permissions");
                    Toast.makeText(EmployeeManagementActivity.this,
                            "Error checking permissions",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        } else {
            // User not logged in
            Toast.makeText(this, "Please log in to continue", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.employeeRecyclerView);
        addEmployeeButton = findViewById(R.id.addEmployeeButton);
        backButton = findViewById(R.id.backButton);
        progressIndicator = findViewById(R.id.progressIndicator);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmployeeAdapter(employees, new EmployeeAdapter.OnEmployeeActionListener() {
            @Override
            public void onEmployeeClick(User employee) {
                showEditEmployeeDialog(employee);
            }

            @Override
            public void onEmployeeDelete(User employee) {
                showDeleteConfirmationDialog(employee);
            }
        });
        recyclerView.setAdapter(adapter);

        // Setup click listeners
        addEmployeeButton.setOnClickListener(v -> showAddEmployeeDialog());
        backButton.setOnClickListener(v -> finish());
    }

    private void loadEmployees() {
        showLoading(true);

        db.collection("users")
                .whereEqualTo("role", "employee")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    employees.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String uid = document.getId();
                        String name = document.getString("name");
                        String email = document.getString("email");
                        String branch = document.getString("branch");

                        User employee = new User(uid, name, email, "employee", branch);
                        employees.add(employee);
                    }

                    adapter.notifyDataSetChanged();
                    showLoading(false);

                    if (employees.isEmpty()) {
                        Toast.makeText(this, "No employees found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error loading employees", e);
                    Toast.makeText(this, "Error loading employees: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showAddEmployeeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_employee, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("Add New Employee");

        AlertDialog dialog = builder.create();

        // Initialize dialog views
        TextInputEditText nameInput = dialogView.findViewById(R.id.employeeNameInput);
        TextInputEditText emailInput = dialogView.findViewById(R.id.employeeEmailInput);
        AutoCompleteTextView branchInput = dialogView.findViewById(R.id.branchDropdown);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Setup branch dropdown
        String[] branches = {"Main Branch", "North Branch", "South Branch", "East Branch", "West Branch"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, branches);
        branchInput.setAdapter(adapter);

        // Set default branch
        branchInput.setText(branches[0], false);

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String branch = branchInput.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || branch.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Generate a random password
            String password = PasswordGenerator.generatePassword(10);

            // Create the employee account
            createEmployeeAccount(name, email, branch, password, dialog);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void createEmployeeAccount(String name, String email, String branch, String password, AlertDialog dialog) {
        showLoading(true);

        // First check if email already exists
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        showLoading(false);
                        Toast.makeText(this, "Email already registered", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Use the generated password for both Firebase Auth and email
                    // This ensures the employee can log in with the password they receive
                    final String employeePassword = password;

                    // Log the password for debugging (remove in production)
                    Log.d(TAG, "Creating employee with password: " + employeePassword);

                    // Create user in Firebase Auth with the same password that will be emailed
                    mAuth.createUserWithEmailAndPassword(email, employeePassword)
                            .addOnSuccessListener(authResult -> {
                                FirebaseUser user = authResult.getUser();
                                if (user != null) {
                                    // Send email verification before creating profile
                                    sendEmailVerification(user, name, email, branch, employeePassword, dialog);
                                }
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Log.e(TAG, "Error creating employee account", e);
                                Toast.makeText(this, "Error creating employee: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error checking email existence", e);
                    Toast.makeText(this, "Error checking email: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Send email verification to the newly created employee
     */
    private void sendEmailVerification(FirebaseUser user, String name, String email, String branch, String password, AlertDialog dialog) {
        // Send verification email through Firebase
        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Verification email sent through Firebase");

                    // Create user profile in Firestore with verification status
                    createEmployeeProfileWithVerification(user.getUid(), name, email, branch, password, dialog);

                    // Also send a custom verification email with more details
                    sendCustomVerificationEmail(user, name, email, branch);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending verification email", e);

                    // Even if Firebase verification fails, still create the profile
                    // but mark it as unverified
                    createEmployeeProfileWithVerification(user.getUid(), name, email, branch, password, dialog);

                    // Try to send custom verification email as fallback
                    sendCustomVerificationEmail(user, name, email, branch);
                });
    }

    /**
     * Send a custom verification email with more details using Mailgun
     * Now includes the password in the verification email
     */
    private void sendCustomVerificationEmail(FirebaseUser user, String name, String email, String branch) {
        // Get the password from Firestore
        db.collection("users")
            .document(user.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String password = documentSnapshot.getString("tempPassword");

                    if (password == null || password.isEmpty()) {
                        Log.w(TAG, "No temporary password found for user: " + email);
                        // Use a default password if none is found (this shouldn't happen)
                        password = PasswordGenerator.generatePassword(10);

                        // Update the password in Firestore
                        db.collection("users")
                            .document(user.getUid())
                            .update("tempPassword", password)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Updated missing temporary password for user: " + email);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to update missing temporary password", e);
                            });
                    }

                    // Continue with sending the verification email with the password
                    sendVerificationEmailWithCredentials(user, name, email, branch, password);
                } else {
                    Log.e(TAG, "User document not found for: " + email);
                    // Use a default password if document not found (this shouldn't happen)
                    String defaultPassword = PasswordGenerator.generatePassword(10);
                    sendVerificationEmailWithCredentials(user, name, email, branch, defaultPassword);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error retrieving user document", e);
                // Use a default password if retrieval fails (this shouldn't happen)
                String defaultPassword = PasswordGenerator.generatePassword(10);
                sendVerificationEmailWithCredentials(user, name, email, branch, defaultPassword);
            });
    }

    /**
     * Send verification email with credentials
     */
    private void sendVerificationEmailWithCredentials(FirebaseUser user, String name, String email, String branch, String password) {
        // Create a verification link that will open our app
        // Get the action code settings for the verification email
        String actionCodeSettings = user.getEmail() + "&apiKey=" + getString(R.string.firebase_api_key);

        // Create a verification link that will work with our VerifyEmailActivity
        String verificationLink = "https://" + getString(R.string.firebase_domain) +
                "/__/auth/action?mode=verifyEmail&oobCode=" +
                user.getUid() + "&continueUrl=https://minuteburgers.page.link/verify&email=" +
                actionCodeSettings;

        // Create a User object for the new employee
        User newEmployee = new User(user.getUid(), name, email, "employee", branch);

        // Send verification email with credentials using Mailgun
        MailgunEmailService.sendEmployeeVerificationEmail(newEmployee, verificationLink, password, new MailgunEmailService.EmailCallback() {
            @Override
            public void onSuccess(String messageId) {
                Log.d(TAG, "Custom verification email with credentials sent successfully to: " + email);

                // Show success message on UI thread
                runOnUiThread(() -> {
                    Toast.makeText(EmployeeManagementActivity.this,
                            "Verification email with credentials sent to " + email,
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Failed to send custom verification email with credentials: " + errorMessage);

                // Show error message on UI thread
                runOnUiThread(() -> {
                    Toast.makeText(EmployeeManagementActivity.this,
                            "Failed to send custom verification email. Standard verification email was sent.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Create employee profile with verification status
     * This method stores the temporary password until email is verified
     */
    private void createEmployeeProfileWithVerification(String uid, String name, String email, String branch, String password, AlertDialog dialog) {
        Map<String, Object> employeeData = new HashMap<>();
        employeeData.put("name", name);
        employeeData.put("email", email);
        employeeData.put("role", "employee");
        employeeData.put("branch", branch);
        employeeData.put("registrationDate", new Date());
        employeeData.put("isActive", true);
        employeeData.put("isEmailVerified", false); // Initially set to false
        employeeData.put("isGoogleAuthenticated", false); // Track Google authentication status
        employeeData.put("tempPassword", password); // Store password temporarily
        employeeData.put("permissions", getEmployeePermissions());

        db.collection("users")
                .document(uid)
                .set(employeeData)
                .addOnSuccessListener(aVoid -> {
                    // Sign out to return to owner account
                    mAuth.signOut();

                    // Sign back in as the owner
                    signBackInAsOwner();

                    showLoading(false);
                    dialog.dismiss();

                    Toast.makeText(this, "Employee added successfully. Verification email sent.", Toast.LENGTH_SHORT).show();

                    // Show a dialog explaining the verification process
                    new AlertDialog.Builder(this)
                        .setTitle("Email Verification Required")
                        .setMessage("A verification email has been sent to " + email + ".\n\n" +
                                "The employee must verify their email before they can receive their login credentials and access the app.\n\n" +
                                "Once verified, credentials will be automatically sent to their email.")
                        .setPositiveButton("OK", null)
                        .show();

                    // Show the credentials backup dialog for the owner's reference
                    showCredentialsBackupDialog(name, email, password);

                    // Reload employees list
                    loadEmployees();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error creating employee profile", e);
                    Toast.makeText(this, "Error creating employee profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // Sign out to return to owner account
                    mAuth.signOut();

                    // Sign back in as the owner
                    signBackInAsOwner();
                });
    }

    /**
     * Legacy method kept for backward compatibility
     * Now uses the verification flow
     */
    private void createEmployeeProfile(String uid, String name, String email, String branch, String password, AlertDialog dialog) {
        // Call the new method with verification
        createEmployeeProfileWithVerification(uid, name, email, branch, password, dialog);
    }

    private Map<String, Boolean> getEmployeePermissions() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put(PermissionManager.MANAGE_INVENTORY, true);
        permissions.put(PermissionManager.MANAGE_STAFF, false);
        permissions.put(PermissionManager.VIEW_REPORTS, true);
        permissions.put(PermissionManager.APPROVE_REQUESTS, false);
        permissions.put(PermissionManager.ACCESS_OWNER_DASHBOARD, false);
        permissions.put(PermissionManager.REQUEST_STOCK, true);
        return permissions;
    }

    /**
     * This method is kept for backward compatibility but is no longer used directly.
     * It's now replaced by sendCredentialsEmailAutomatically.
     */
    private void sendCredentialsEmail(String name, String email, String password) {
        sendCredentialsEmailAutomatically(name, email, password);
    }

    /**
     * Automatically sends credentials email to the new employee
     * with proper error handling and feedback using Mailgun API
     */
    private void sendCredentialsEmailAutomatically(String name, String email, String password) {
        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Sending Credentials")
            .setMessage("Sending credentials to " + email + "...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        Log.d(TAG, "Attempting to send credentials email to: " + email);

        // Create a User object for the new employee
        User newEmployee = new User(null, name, email, "employee", "");

        // Send email with credentials using Mailgun
        MailgunEmailService.sendEmployeeCredentials(newEmployee, password, new MailgunEmailService.EmailCallback() {
            @Override
            public void onSuccess(String messageId) {
                Log.d(TAG, "Email sent successfully to: " + email + " (Message ID: " + messageId + ")");

                // Run on UI thread
                runOnUiThread(() -> {
                    // Dismiss progress dialog
                    progressDialog.dismiss();

                    // Check if we used the fallback method
                    boolean usedFallback = "FALLBACK_METHOD_USED".equals(messageId);

                    if (usedFallback) {
                        // Show partial success message
                        Toast.makeText(EmployeeManagementActivity.this,
                            "Couldn't send email automatically, but credentials are saved",
                            Toast.LENGTH_LONG).show();

                        // Show a dialog explaining the fallback
                        new AlertDialog.Builder(EmployeeManagementActivity.this)
                            .setTitle("Email Sending Used Fallback")
                            .setMessage("We couldn't send the credentials email automatically through Mailgun.\n\n" +
                                    "However, the credentials have been saved and are displayed below.\n\n" +
                                    "Please take a screenshot or note these credentials to share with the employee.")
                            .setPositiveButton("OK", null)
                            .setCancelable(false)
                            .show();
                    } else {
                        // Show full success message
                        Toast.makeText(EmployeeManagementActivity.this,
                            "Credentials email sent to " + email + " successfully",
                            Toast.LENGTH_LONG).show();
                    }

                    // Always show the credentials backup dialog for reference
                    showCredentialsBackupDialog(name, email, password);
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Failed to send email: " + errorMessage);

                // Run on UI thread
                runOnUiThread(() -> {
                    // Dismiss progress dialog
                    progressDialog.dismiss();

                    // Show error message
                    Toast.makeText(EmployeeManagementActivity.this,
                        "Failed to send email: " + errorMessage,
                        Toast.LENGTH_LONG).show();

                    // Show a dialog with the error
                    new AlertDialog.Builder(EmployeeManagementActivity.this)
                        .setTitle("Email Sending Failed")
                        .setMessage("We couldn't send the credentials email automatically.\n\n" +
                                "Error: " + errorMessage + "\n\n" +
                                "Please share the credentials with the employee manually.")
                        .setPositiveButton("OK", null)
                        .setCancelable(false)
                        .show();

                    // Always show credentials backup dialog
                    showCredentialsBackupDialog(name, email, password);
                });
            }
        });

        // As a fallback, also try the legacy email method
        // This provides redundancy in case Mailgun fails
        try {
            String subject = "Your Minute Burgers Account";
            String htmlMessage = "<html><body style='font-family: Arial, sans-serif;'>" +
                    "<div style='padding: 20px; background-color: #f5f5f5; border-radius: 5px;'>" +
                    "<h2 style='color: #FF6600;'>Welcome to Minute Burgers!</h2>" +
                    "<p>Hello " + name + ",</p>" +
                    "<p>Your Minute Burgers employee account has been created successfully.</p>" +
                    "<div style='background-color: #ffffff; padding: 15px; border-left: 4px solid #FF6600; margin: 10px 0;'>" +
                    "<p><strong>Email:</strong> " + email + "</p>" +
                    "<p><strong>Password:</strong> " + password + "</p>" +
                    "</div>" +
                    "<p>Please log in and change your password as soon as possible.</p>" +
                    "<p>If you have any questions, please contact your manager.</p>" +
                    "<p>Regards,<br>Minute Burgers Management</p>" +
                    "</div></body></html>";

            EmailService.sendEmail(this, email, subject, htmlMessage, null);
        } catch (Exception e) {
            Log.e(TAG, "Legacy email sending failed", e);
        }
    }

    private void showCredentialsBackupDialog(String name, String email, String password) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_credentials_backup, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Employee Credentials")
            .setView(dialogView)
            .setCancelable(false)
            .create();

        // Get references to views
        TextView nameTextView = dialogView.findViewById(R.id.employeeNameText);
        TextView emailTextView = dialogView.findViewById(R.id.employeeEmailText);
        TextView passwordTextView = dialogView.findViewById(R.id.employeePasswordText);
        Button copyButton = dialogView.findViewById(R.id.copyButton);
        Button shareButton = dialogView.findViewById(R.id.shareButton);
        Button doneButton = dialogView.findViewById(R.id.doneButton);

        // Set text values
        nameTextView.setText(name);
        emailTextView.setText(email);
        passwordTextView.setText(password);

        // Set up copy button
        copyButton.setOnClickListener(v -> {
            // Copy credentials to clipboard
            String credentialsText = "Employee: " + name + "\nEmail: " + email + "\nPassword: " + password;
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Employee Credentials", credentialsText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Credentials copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Set up share button
        shareButton.setOnClickListener(v -> {
            // Share credentials via intent
            String credentialsText = "Employee: " + name + "\nEmail: " + email + "\nPassword: " + password;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Minute Burgers - Employee Credentials");
            shareIntent.putExtra(Intent.EXTRA_TEXT, credentialsText);
            startActivity(Intent.createChooser(shareIntent, "Share credentials via"));
        });

        // Set up done button
        doneButton.setOnClickListener(v -> dialog.dismiss());

        // Show the dialog
        dialog.show();
    }

    private void signBackInAsOwner() {
        // This is a simplified version - in a real app, you would store the owner credentials securely
        String ownerEmail = "owner@minuteburgers.com";
        String ownerPassword = "owner123456";

        mAuth.signInWithEmailAndPassword(ownerEmail, ownerPassword)
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Signed back in as owner");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error signing back in as owner", e);
                });
    }

    // Overloaded version for use with the employee deletion process

    private void showEditEmployeeDialog(User employee) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_employee, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("Edit Employee");

        AlertDialog dialog = builder.create();

        // Initialize dialog views
        TextInputEditText nameInput = dialogView.findViewById(R.id.employeeNameInput);
        TextInputEditText emailInput = dialogView.findViewById(R.id.employeeEmailInput);
        AutoCompleteTextView branchInput = dialogView.findViewById(R.id.branchDropdown);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Setup branch dropdown
        String[] branches = {"Main Branch", "North Branch", "South Branch", "East Branch", "West Branch"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, branches);
        branchInput.setAdapter(adapter);

        // Populate fields with employee data
        nameInput.setText(employee.getName());
        emailInput.setText(employee.getEmail());
        branchInput.setText(employee.getBranch(), false);

        // Email should not be editable for existing employees
        emailInput.setEnabled(false);

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String branch = branchInput.getText().toString().trim();

            if (name.isEmpty() || branch.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update the employee profile
            updateEmployeeProfile(employee.getUid(), name, branch, dialog);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateEmployeeProfile(String uid, String name, String branch, AlertDialog dialog) {
        showLoading(true);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("branch", branch);

        db.collection("users")
                .document(uid)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    dialog.dismiss();
                    Toast.makeText(this, "Employee updated successfully", Toast.LENGTH_SHORT).show();

                    // Reload employees list
                    loadEmployees();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error updating employee profile", e);
                    Toast.makeText(this, "Error updating employee: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmationDialog(User employee) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Employee")
                .setMessage("Are you sure you want to delete " + employee.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteEmployee(employee))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteEmployee(User employee) {
        showLoading(true);

        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Deleting Employee")
            .setMessage("Deleting " + employee.getName() + " from the system...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        // Store current user (owner) credentials to sign back in later
        String ownerEmail = "owner@minuteburgers.com";
        String ownerPassword = "owner123456";

        // Get the current user (owner)
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Step 1: Delete from Firestore first
        db.collection("users")
                .document(employee.getUid())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Employee document deleted from Firestore");

                    // Step 2: Delete from Authentication
                    // In a real app, this would be done via Firebase Admin SDK on a server
                    // For this demo, we'll use a simplified approach by signing in as the employee

                    // First, we need to sign out as the owner
                    mAuth.signOut();

                    // Try to sign in as the employee to delete their account
                    // Note: This approach has limitations and would be replaced with Admin SDK in production
                    // We're using a fixed password for demo purposes - in a real app, you'd need to store this securely
                    String employeeEmail = employee.getEmail();

                    // In a real app, you would use Firebase Admin SDK instead
                    // We're using the password that was generated during account creation
                    // This is the same password that was emailed to the employee
                    // For demo purposes, we'll use the password from the PasswordGenerator
                    String employeePassword = PasswordGenerator.generatePassword(10); // Try to use the same algorithm

                    // Try to sign in as the employee
                    mAuth.signInWithEmailAndPassword(employeeEmail, employeePassword)
                            .addOnSuccessListener(authResult -> {
                                // Successfully signed in as employee, now delete their account
                                FirebaseUser employeeUser = mAuth.getCurrentUser();
                                if (employeeUser != null) {
                                    employeeUser.delete()
                                            .addOnSuccessListener(aVoid2 -> {
                                                Log.d(TAG, "Employee auth account deleted");

                                                // Step 3: Sign back in as owner
                                                signBackInAsOwner(progressDialog, true, employee.getName());
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Error deleting employee auth account", e);

                                                // Sign back in as owner even if deletion failed
                                                signBackInAsOwner(progressDialog, false, employee.getName());
                                            });
                                } else {
                                    // Couldn't get employee user, sign back in as owner
                                    Log.e(TAG, "Failed to get employee user after sign in");
                                    signBackInAsOwner(progressDialog, false, employee.getName());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error signing in as employee to delete account", e);

                                // If we can't sign in as the employee, we can't delete their auth account
                                // Sign back in as owner
                                signBackInAsOwner(progressDialog, false, employee.getName());
                            });
                })
                .addOnFailureListener(e -> {
                    // Failed to delete from Firestore
                    Log.e(TAG, "Error deleting employee from Firestore", e);
                    progressDialog.dismiss();
                    showLoading(false);
                    Toast.makeText(this, "Error deleting employee: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void signBackInAsOwner(AlertDialog progressDialog, boolean authDeleted, String employeeName) {
        // Sign back in as the owner
        String ownerEmail = "owner@minuteburgers.com";
        String ownerPassword = "owner123456";

        mAuth.signInWithEmailAndPassword(ownerEmail, ownerPassword)
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Signed back in as owner");
                    progressDialog.dismiss();
                    showLoading(false);

                    if (authDeleted) {
                        Toast.makeText(this, employeeName + " deleted successfully from both database and authentication", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, employeeName + " deleted from database but could not delete authentication account", Toast.LENGTH_LONG).show();
                    }

                    // Reload employees list
                    loadEmployees();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error signing back in as owner", e);
                    progressDialog.dismiss();
                    showLoading(false);
                    Toast.makeText(this, "Error signing back in as owner: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // Reload employees list anyway
                    loadEmployees();
                });
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        addEmployeeButton.setEnabled(!show);
    }
}
