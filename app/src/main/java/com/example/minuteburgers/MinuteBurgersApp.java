package com.example.minuteburgers;

import android.app.Application;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.example.minuteburgers.utils.AppConfig;
import com.google.firebase.FirebaseApp;

import java.util.Map;

public class MinuteBurgersApp extends Application {
    private static final String TAG = "MinuteBurgersApp";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully in Application class");

            // Initialize Cloudinary with centralized configuration
            initializeCloudinary();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing app services: ", e);
        }
    }

    /**
     * Initialize Cloudinary with proper configuration from AppConfig
     */
    private void initializeCloudinary() {
        try {
            // Check if MediaManager is already initialized
            boolean isInitialized = false;
            try {
                // Try to get the instance - if it works, it's initialized
                MediaManager.get();
                isInitialized = true;
                Log.d(TAG, "Cloudinary already initialized");
            } catch (IllegalStateException e) {
                // Not initialized yet
                isInitialized = false;
            }

            // Only initialize if not already initialized
            if (!isInitialized) {
                // Get configuration from AppConfig
                Map<String, String> config = AppConfig.getCloudinaryConfig();

                // Initialize MediaManager with configuration
                MediaManager.init(this, config);
                Log.d(TAG, "Cloudinary initialized successfully with cloud name: " + AppConfig.CLOUDINARY_CLOUD_NAME);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Cloudinary", e);
        }
    }
}