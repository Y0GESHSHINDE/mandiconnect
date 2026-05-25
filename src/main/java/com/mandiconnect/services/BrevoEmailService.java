package com.mandiconnect.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class BrevoEmailService {

    private final RestTemplate restTemplate;
    private final String brevoBaseUrl;
    private final String brevoApiKey;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailService(
            RestTemplate restTemplate,
            @Value("${brevo.base-url:https://api.brevo.com/v3}") String brevoBaseUrl,
            @Value("${brevo.api-key:}") String brevoApiKey,
            @Value("${brevo.sender-email:}") String senderEmail,
            @Value("${brevo.sender-name:MandiConnect}") String senderName
    ) {
        this.restTemplate = restTemplate;
        this.brevoBaseUrl = brevoBaseUrl;
        this.brevoApiKey = brevoApiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        validateConfiguration();

        log.info("Sending email via Brevo to {} with subject '{}'", toEmail, subject);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", brevoApiKey.trim());

        BrevoSendEmailRequest payload = new BrevoSendEmailRequest(
                new BrevoSender(senderEmail.trim(), senderName),
                List.of(new BrevoRecipient(toEmail)),
                subject,
                htmlContent
        );

        HttpEntity<BrevoSendEmailRequest> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<BrevoSendEmailResponse> response = restTemplate.postForEntity(
                    normalizeBaseUrl() + "/smtp/email",
                    request,
                    BrevoSendEmailResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Brevo email request failed with status " + response.getStatusCode().value());
            }

            String messageId = response.getBody() != null ? response.getBody().messageId() : null;
            log.info(
                    "Brevo accepted email for {}. status={}, messageId={}",
                    toEmail,
                    response.getStatusCode().value(),
                    messageId != null ? messageId : "N/A"
            );
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(resolveErrorMessage(ex), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send email using Brevo API", ex);
        }
    }

    private void validateConfiguration() {
        if (isBlank(brevoApiKey)) {
            throw new IllegalStateException("Brevo API key is not configured");
        }
        if (isBlank(senderEmail)) {
            throw new IllegalStateException("Brevo sender email is not configured");
        }
    }

    private String normalizeBaseUrl() {
        String safeBaseUrl = isBlank(brevoBaseUrl) ? "https://api.brevo.com/v3" : brevoBaseUrl.trim();
        return safeBaseUrl.endsWith("/") ? safeBaseUrl.substring(0, safeBaseUrl.length() - 1) : safeBaseUrl;
    }

    private String resolveErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (!isBlank(body)) {
            log.warn("Brevo email API error response: {}", body);
            return "Brevo email API request failed: " + body;
        }
        return "Brevo email API request failed with status " + ex.getStatusCode().value();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BrevoSendEmailResponse(String messageId) {
    }

    private record BrevoSendEmailRequest(
            BrevoSender sender,
            List<BrevoRecipient> to,
            String subject,
            String htmlContent
    ) {
    }

    private record BrevoSender(String email, String name) {
    }

    private record BrevoRecipient(String email) {
    }
}
