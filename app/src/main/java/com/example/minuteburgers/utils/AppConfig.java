package com.example.minuteburgers.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for application-wide settings
 */
public class AppConfig {
    // Mailgun API configuration
    public static final String MAILGUN_API_BASE_URL = "https://api.mailgun.net/v3/";
    public static final String MAILGUN_DOMAIN = "sandbox5e9d4a9e9c1c4c9a9e9d4a9e9c1c4c9a.mailgun.org"; // Replace with your actual Mailgun domain
    public static final String MAILGUN_API_KEY = "key-5e9d4a9e9c1c4c9a9e9d4a9e9c1c4c9a"; // Replace with your actual Mailgun API key
    public static final String SENDER_EMAIL = "postmaster@sandbox5e9d4a9e9c1c4c9a9e9d4a9e9c1c4c9a.mailgun.org"; // Must be authorized sender for your domain
    public static final String SENDER_NAME = "Minute Burgers";

    // Cloudinary configuration
    public static final String CLOUDINARY_CLOUD_NAME = "djf2atu0h";
    public static final String CLOUDINARY_API_KEY = "216549336527232";
    public static final String CLOUDINARY_API_SECRET = "VxyHB2o9pzjfNpXQzWNFYfsSJ6I";

    /**
     * Get Cloudinary configuration map
     * @return Map with Cloudinary configuration
     */
    public static Map<String, String> getCloudinaryConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", CLOUDINARY_CLOUD_NAME);
        config.put("api_key", CLOUDINARY_API_KEY);
        config.put("api_secret", CLOUDINARY_API_SECRET);
        config.put("secure", "true");  // Force HTTPS
        return config;
    }
}
