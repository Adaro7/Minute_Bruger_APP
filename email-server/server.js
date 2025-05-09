const express = require('express');
const bodyParser = require('body-parser');
const nodemailer = require('nodemailer');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Create a transporter object using SMTP transport
const transporter = nodemailer.createTransport({
  service: 'gmail',
  auth: {
    user: 'your-email@gmail.com', // Replace with your Gmail address
    pass: 'your-app-password'     // Replace with your Gmail app password
  }
});

// Route for sending emails
app.post('/send-email', async (req, res) => {
  try {
    const { to, subject, message, from } = req.body;
    
    if (!to || !subject || !message) {
      return res.status(400).json({ 
        success: false, 
        message: 'Missing required fields (to, subject, message)' 
      });
    }

    // Email options
    const mailOptions = {
      from: from || 'Minute Burgers <noreply@minuteburgers.com>',
      to: to,
      subject: subject,
      html: message
    };

    // Send email
    const info = await transporter.sendMail(mailOptions);
    
    console.log('Email sent: ' + info.response);
    res.status(200).json({ 
      success: true, 
      message: 'Email sent successfully',
      messageId: info.messageId
    });
  } catch (error) {
    console.error('Error sending email:', error);
    res.status(500).json({ 
      success: false, 
      message: 'Error sending email',
      error: error.message
    });
  }
});

// Route for sending bulk emails
app.post('/send-bulk-email', async (req, res) => {
  try {
    const { to, subject, message, from } = req.body;
    
    if (!to || !Array.isArray(to) || !subject || !message) {
      return res.status(400).json({ 
        success: false, 
        message: 'Missing required fields (to as array, subject, message)' 
      });
    }

    // Email options
    const mailOptions = {
      from: from || 'Minute Burgers <noreply@minuteburgers.com>',
      bcc: to, // Use BCC for bulk emails to protect recipient privacy
      subject: subject,
      html: message
    };

    // Send email
    const info = await transporter.sendMail(mailOptions);
    
    console.log('Bulk email sent: ' + info.response);
    res.status(200).json({ 
      success: true, 
      message: 'Bulk email sent successfully',
      messageId: info.messageId,
      recipientCount: to.length
    });
  } catch (error) {
    console.error('Error sending bulk email:', error);
    res.status(500).json({ 
      success: false, 
      message: 'Error sending bulk email',
      error: error.message
    });
  }
});

// Health check route
app.get('/', (req, res) => {
  res.status(200).json({ 
    status: 'OK', 
    message: 'Email server is running' 
  });
});

// Start the server
app.listen(PORT, () => {
  console.log(`Email server running on port ${PORT}`);
});
