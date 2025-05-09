# Mailgun Setup Instructions for Minute Burgers App

This guide will help you properly set up Mailgun to send employee credentials emails from the Minute Burgers app.

## Why You're Seeing HTTP 401 Errors

The HTTP 401 error indicates an authentication problem with the Mailgun API. This typically happens because:

1. The API key is incorrect or invalid
2. The domain is not properly configured
3. The sender email is not authorized for your domain

## Step 1: Create a Mailgun Account

1. Go to [Mailgun's website](https://www.mailgun.com/) and sign up for an account
2. Verify your account by following the instructions sent to your email
3. Complete any required verification steps

## Step 2: Add and Verify a Domain

1. In your Mailgun dashboard, go to "Domains" and click "Add New Domain"
2. You have two options:
   - **For testing**: Use a Mailgun Sandbox domain (free, limited features)
   - **For production**: Add your own custom domain (requires DNS verification)

### Using a Sandbox Domain (Easiest for Testing)

1. In your Mailgun dashboard, look for your Sandbox domain (it will look like `sandbox123abc.mailgun.org`)
2. Note the domain name and the API key
3. Note the authorized sender email (usually `postmaster@sandbox123abc.mailgun.org`)

### Using Your Own Domain (Recommended for Production)

1. Add your domain (e.g., `minuteburgers.com`)
2. Follow the DNS verification instructions provided by Mailgun
3. Wait for DNS changes to propagate (this can take up to 24-48 hours)
4. Verify your domain is active in the Mailgun dashboard

## Step 3: Get Your API Key

1. In your Mailgun dashboard, go to "API Keys"
2. Copy your "Private API Key" (it starts with "key-")

## Step 4: Update the App Configuration

1. Open the file `app/src/main/java/com/example/minuteburgers/utils/AppConfig.java`
2. Replace the placeholder values with your actual Mailgun credentials:

```java
// Mailgun API configuration
public static final String MAILGUN_API_BASE_URL = "https://api.mailgun.net/v3/";
public static final String MAILGUN_DOMAIN = "your-domain.mailgun.org"; // Your verified Mailgun domain
public static final String MAILGUN_API_KEY = "key-xxxxxxxxxxxxxxxxxxxx"; // Your Mailgun API key
public static final String SENDER_EMAIL = "noreply@your-domain.mailgun.org"; // Must be from your verified domain
public static final String SENDER_NAME = "Minute Burgers";
```

**IMPORTANT NOTES:**
- For sandbox domains, use the full sandbox domain name (e.g., `sandbox123abc.mailgun.org`)
- For sender email, you MUST use an authorized sender for your domain:
  - For sandbox domains: Use `postmaster@sandbox123abc.mailgun.org`
  - For custom domains: Use any email on your verified domain

## Step 5: Test the Integration

1. Build and run the app
2. Add a new employee with a valid email address
3. Check if the email is received successfully

## Troubleshooting Common Issues

### 1. HTTP 401 Unauthorized

**Causes:**
- Incorrect API key
- API key not prefixed with "key-"
- Using the public API key instead of the private API key

**Solution:**
- Double-check your API key in the Mailgun dashboard
- Make sure you're using the Private API Key, not the Public API Key
- Ensure the API key is correctly copied with the "key-" prefix

### 2. HTTP 404 Not Found

**Causes:**
- Incorrect domain name
- Domain not properly configured in Mailgun

**Solution:**
- Verify your domain name in the Mailgun dashboard
- Make sure you're using the full domain name (e.g., `sandbox123abc.mailgun.org`)

### 3. HTTP 400 Bad Request

**Causes:**
- Invalid sender email
- Sender email not authorized for your domain

**Solution:**
- For sandbox domains, you can only send from `postmaster@your-sandbox-domain.mailgun.org`
- For custom domains, the sender email must be on your verified domain

### 4. Emails Not Being Received

**Causes:**
- Emails going to spam folder
- Rate limits exceeded
- Recipient email server rejecting the emails

**Solution:**
- Check spam/junk folders
- For sandbox domains, you can only send to authorized recipients
- Add authorized recipients in your Mailgun dashboard

## Fallback Mechanism

The app includes a fallback mechanism that will display the credentials in the app if email sending fails. This ensures you can still provide the credentials to employees even if there are issues with Mailgun.

## Need More Help?

- Check the [Mailgun Documentation](https://documentation.mailgun.com/)
- Review the logs in Android Studio for detailed error messages
- Contact the app developer for assistance
