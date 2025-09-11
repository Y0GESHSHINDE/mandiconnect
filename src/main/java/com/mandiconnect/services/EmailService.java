package com.mandiconnect.services;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl; // Dynamic base URL

    public void sendVerificationEmail(String toEmail, String token) {
        try {
            String subject = "MandiConnect - Verify Your Email";
            String link = baseUrl + "/farmer/verify?token=" + token;

            // HTML Email Template
            String body = """
                    <div style="font-family: Arial, sans-serif; color: #333; padding: 20px;">
                        <h2 style="color: #2E7D32;">Welcome to MandiConnect 🌾</h2>
                        <p>Dear Farmer,</p>
                        <p>Thank you for registering with <b>MandiConnect</b>. To complete your sign-up, please verify your email address by clicking the button below:</p>
                        
                        <a href="%s" style="display:inline-block; margin: 20px 0; padding: 10px 20px;
                           background-color: #2E7D32; color: #fff; text-decoration: none; border-radius: 5px;">
                           Verify Email
                        </a>
                        
                        <p>If the button doesn’t work, copy and paste the following link into your browser:</p>
                        <p><a href="%s">%s</a></p>
                        
                        <br/>
                        <p style="font-size: 12px; color: #777;">This email was sent by MandiConnect. Please do not reply directly.</p>
                    </div>
                    """.formatted(link, link, link);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true); // HTML content

            mailSender.send(message);

            System.out.println("📩 Verification email sent to: " + toEmail + " | Link: " + link);

        } catch (Exception e) {
            System.out.println("❌ Failed to send email: " + e.getMessage());
        }
    }
}
