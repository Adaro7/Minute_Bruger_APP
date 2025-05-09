# Mailgun Email Integration Setup

This document provides instructions on how to set up Mailgun for sending emails directly from the Minute Burgers app.

## What is Mailgun?

Mailgun is a transactional email API service that allows you to send emails directly from your application. It provides reliable email delivery, tracking, and analytics.

## Why Mailgun?

- **Reliability**: High deliverability rates
- **Scalability**: Can handle large volumes of emails
- **Analytics**: Track email opens, clicks, and other metrics
- **API-based**: Easy to integrate with mobile applications
- **Free tier**: Includes 10,000 emails per month for 3 months

## Setup Instructions

### 1. Create a Mailgun Account

1. Go to [Mailgun's website](https://www.mailgun.com/) and sign up for an account
2. Verify your account by following the instructions sent to your email

### 2. Add and Verify a Domain

1. In your Mailgun dashboard, go to "Domains" and click "Add New Domain"
2. Enter your domain name (e.g., minuteburgers.com) or create a subdomain (e.g., mail.minuteburgers.com)
3. Follow the DNS verification instructions provided by Mailgun
4. Wait for DNS changes to propagate (this can take up to 24-48 hours)

### 3. Get Your API Key

1. In your Mailgun dashboard, go to "API Keys"
2. Copy your "Private API Key"

### 4. Update the App Configuration

1. Open the file `app/src/main/java/com/example/minuteburgers/utils/AppConfig.java`
2. Replace the placeholder values with your actual Mailgun credentials:

```java
// Mailgun API configuration
public static final String MAILGUN_DOMAIN = "your-domain.com"; // Your verified Mailgun domain
public static final String MAILGUN_API_KEY = "key-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"; // Your Mailgun API key
public static final String SENDER_EMAIL = "noreply@your-domain.com"; // Must be from your verified domain
```

### 5. Test the Integration

1. Build and run the app
2. Add a new employee with a valid email address
3. Check if the email is received successfully

## Troubleshooting

### Email Not Received

1. Check the app logs for any error messages
2. Verify that your Mailgun domain is properly set up and verified
3. Make sure your API key is correct
4. Check if the recipient's email address is valid
5. Look for the email in spam/junk folders

### API Errors

Common Mailgun API error codes:

- **401 Unauthorized**: Invalid API key
- **400 Bad Request**: Malformed request (check sender/recipient email formats)
- **402 Request Failed**: Free trial or subscription limits exceeded
- **404 Not Found**: Invalid endpoint or domain not found
- **500 Server Errors**: Temporary Mailgun service issues

## Additional Resources

- [Mailgun Documentation](https://documentation.mailgun.com/)
- [Mailgun API Reference](https://documentation.mailgun.com/en/latest/api_reference.html)
- [Mailgun Java SDK](https://github.com/mailgun/mailgun-java)

## Support

If you encounter any issues with the Mailgun integration, please contact the app developer or refer to the Mailgun documentation for further assistance.
