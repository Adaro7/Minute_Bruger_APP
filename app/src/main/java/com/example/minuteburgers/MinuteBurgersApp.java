package com.example.minuteburgers;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;

public class MinuteBurgersApp extends Application {
    private static final String TAG = "MinuteBurgersApp";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully in Application class");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: ", e);
        }
    }
} 