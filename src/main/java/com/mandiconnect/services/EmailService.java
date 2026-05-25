package com.mandiconnect.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final BrevoEmailService brevoEmailService;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendVerificationEmail(String toEmail, String token) {
        try {
            String subject = "MandiConnect - Verify Your Email";
            String link = baseUrl + "/farmer/verify?token=" + token;

            String body = """
                    <div style="font-family: Arial, sans-serif; color: #333; padding: 20px;">
                        <h2 style="color: #2E7D32;">Welcome to MandiConnect ðŸŒ¾</h2>
                        <p>Dear Farmer,</p>
                        <p>Thank you for registering with <b>MandiConnect</b>. To complete your sign-up, please verify your email address by clicking the button below:</p>
                        
                        <a href="%s" style="display:inline-block; margin: 20px 0; padding: 10px 20px;
                           background-color: #2E7D32; color: #fff; text-decoration: none; border-radius: 5px;">
                           Verify Email
                        </a>
                        
                        <p>If the button doesnâ€™t work, copy and paste the following link into your browser:</p>
                        <p><a href="%s">%s</a></p>
                        
                        <br/>
                        <p style="font-size: 12px; color: #777;">This email was sent by MandiConnect. Please do not reply directly.</p>
                    </div>
                    """.formatted(link, link, link);

            brevoEmailService.sendHtmlEmail(toEmail, subject, body);
            log.info("Verification email sent to farmer {}", toEmail);
        } catch (Exception ex) {
            log.error("Failed to send farmer verification email to {}", toEmail, ex);
            System.err.println("Failed to send farmer verification email to " + toEmail + ": " + ex.getMessage());
        }
    }

    public void sendVerificationEmailForBuyer(String toEmail, String token) {
        try {
            String subject = "MandiConnect - Verify Your Email";
            String link = baseUrl + "/buyer/verify?token=" + token;

            String body = """
                    <div style="font-family: Arial, sans-serif; color: #333; padding: 20px;">
                        <h2 style="color: #2E7D32;">Welcome to MandiConnect ðŸŒ¾</h2>
                        <p>Dear Buyer,</p>
                        <p>Thank you for registering with <b>MandiConnect</b>. To complete your sign-up, please verify your email address by clicking the button below:</p>
                        
                        <a href="%s" style="display:inline-block; margin: 20px 0; padding: 10px 20px;
                           background-color: #2E7D32; color: #fff; text-decoration: none; border-radius: 5px;">
                           Verify Email
                        </a>
                        
                        <p>If the button doesnâ€™t work, copy and paste the following link into your browser:</p>
                        <p><a href="%s">%s</a></p>
                        
                        <br/>
                        <p style="font-size: 12px; color: #777;">This email was sent by MandiConnect. Please do not reply directly.</p>
                    </div>
                    """.formatted(link, link, link);

            brevoEmailService.sendHtmlEmail(toEmail, subject, body);
            log.info("Verification email sent to buyer {}", toEmail);
        } catch (Exception ex) {
            log.error("Failed to send buyer verification email to {}", toEmail, ex);
            System.err.println("Failed to send buyer verification email to " + toEmail + ": " + ex.getMessage());
        }
    }
}
