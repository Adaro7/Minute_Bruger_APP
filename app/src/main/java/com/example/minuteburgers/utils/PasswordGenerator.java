package com.example.minuteburgers.utils;

import java.security.SecureRandom;

/**
 * Utility class for generating secure random passwords
 */
public class PasswordGenerator {
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()_-+=<>?";
    private static final String ALL_CHARS = LOWERCASE + UPPERCASE + DIGITS + SPECIAL;
    
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Generates a secure random password of the specified length
     * The password will contain at least one lowercase letter, one uppercase letter,
     * one digit, and one special character
     * 
     * @param length The length of the password to generate (minimum 8)
     * @return A secure random password
     */
    public static String generatePassword(int length) {
        // Ensure minimum length
        if (length < 8) {
            length = 8;
        }
        
        // Create a char array to store the password
        char[] password = new char[length];
        
        // Ensure at least one of each character type
        password[0] = LOWERCASE.charAt(random.nextInt(LOWERCASE.length()));
        password[1] = UPPERCASE.charAt(random.nextInt(UPPERCASE.length()));
        password[2] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        password[3] = SPECIAL.charAt(random.nextInt(SPECIAL.length()));
        
        // Fill the rest with random characters
        for (int i = 4; i < length; i++) {
            password[i] = ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length()));
        }
        
        // Shuffle the password to avoid predictable patterns
        for (int i = 0; i < length; i++) {
            int randomPosition = random.nextInt(length);
            char temp = password[i];
            password[i] = password[randomPosition];
            password[randomPosition] = temp;
        }
        
        return new String(password);
    }
}
