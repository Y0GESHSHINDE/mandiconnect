package com.mandiconnect.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mandiconnect.models.Notification;
import com.mandiconnect.models.NotificationDeviceToken;
import com.mandiconnect.repositories.NotificationDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPushService {

    private final NotificationDeviceTokenRepository notificationDeviceTokenRepository;

    @Value("${expo.push.enabled:false}")
    private boolean expoPushEnabled;

    @Value("${expo.push.api-url:https://exp.host/--/api/v2/push/send}")
    private String expoPushApiUrl;

    @Value("${expo.push.access-token:}")
    private String expoPushAccessToken;

    @Value("${expo.push.channel-id:default}")
    private String expoPushChannelId;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendNotification(Notification notification) {
        if (!expoPushEnabled || notification == null || notification.getUserId() == null || notification.getUserId().isBlank()) {
            return;
        }

        List<NotificationDeviceToken> activeTokens =
                notificationDeviceTokenRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(notification.getUserId());

        List<NotificationDeviceToken> expoTokens = activeTokens.stream()
                .filter(this::isExpoToken)
                .toList();

        if (expoTokens.isEmpty()) {
            return;
        }

        try {
            String requestBody = buildExpoPushRequest(notification, expoTokens);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(expoPushApiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

            String accessToken = normalizeOptional(expoPushAccessToken);
            if (accessToken != null) {
                requestBuilder.header("Authorization", "Bearer " + accessToken);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Expo push request failed with status {} and body {}", response.statusCode(), response.body());
                return;
            }

            processExpoPushResponse(expoTokens, response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to deliver Expo push notification for notification {}", notification.getId(), ex);
        } catch (Exception ex) {
            log.warn("Unexpected Expo push delivery failure for notification {}", notification.getId(), ex);
        }
    }

    private String buildExpoPushRequest(Notification notification, List<NotificationDeviceToken> tokens) throws IOException {
        ArrayNode messages = objectMapper.createArrayNode();

        for (NotificationDeviceToken token : tokens) {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("to", token.getPushToken());
            message.put("title", defaultIfBlank(notification.getTitle(), "MandiConnect"));
            message.put("body", defaultIfBlank(notification.getMessage(), "You have a new update"));
            message.put("sound", "default");
            message.put("priority", "high");
            message.put("channelId", defaultIfBlank(expoPushChannelId, "default"));

            ObjectNode data = objectMapper.createObjectNode();
            putIfPresent(data, "notificationId", notification.getId());
            putIfPresent(data, "type", notification.getType());
            putIfPresent(data, "connectionId", notification.getConnectionId());
            putIfPresent(data, "chatId", notification.getChatId());
            putIfPresent(data, "orderId", notification.getOrderId());
            putIfPresent(data, "orderCode", notification.getOrderCode());
            putIfPresent(data, "paymentTransactionId", notification.getPaymentTransactionId());
            putIfPresent(data, "referenceType", notification.getReferenceType());
            putIfPresent(data, "referenceId", notification.getReferenceId());
            putIfPresent(data, "actorUserId", notification.getActorUserId());
            putIfPresent(data, "actorUserRole", notification.getActorUserRole());

            if (!data.isEmpty()) {
                message.set("data", data);
            }

            messages.add(message);
        }

        return objectMapper.writeValueAsString(messages);
    }

    private void processExpoPushResponse(List<NotificationDeviceToken> sentTokens, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            return;
        }

        List<NotificationDeviceToken> toDeactivate = new ArrayList<>();
        int limit = Math.min(sentTokens.size(), data.size());
        for (int index = 0; index < limit; index++) {
            JsonNode ticket = data.get(index);
            if (ticket == null) {
                continue;
            }

            String status = normalizeOptional(ticket.path("status").asText(null));
            if (!"error".equalsIgnoreCase(status)) {
                continue;
            }

            String errorCode = normalizeOptional(ticket.path("details").path("error").asText(null));
            if ("DeviceNotRegistered".equalsIgnoreCase(errorCode)) {
                NotificationDeviceToken token = sentTokens.get(index);
                if (token != null && token.isActive()) {
                    token.setActive(false);
                    token.setUpdatedAt(LocalDateTime.now());
                    token.setLastSeenAt(LocalDateTime.now());
                    toDeactivate.add(token);
                }
            } else if (sentTokens.get(index) != null) {
                log.warn("Expo push returned error {} for device token {}", errorCode, sentTokens.get(index).getId());
            }
        }

        if (!toDeactivate.isEmpty()) {
            notificationDeviceTokenRepository.saveAll(toDeactivate);
        }
    }

    private boolean isExpoToken(NotificationDeviceToken token) {
        if (token == null || !token.isActive()) {
            return false;
        }

        String provider = normalizeOptional(token.getProvider());
        String pushToken = normalizeOptional(token.getPushToken());
        if (pushToken == null) {
            return false;
        }

        if (provider != null && !"EXPO".equalsIgnoreCase(provider)) {
            return false;
        }

        return pushToken.startsWith("ExponentPushToken[") || pushToken.startsWith("ExpoPushToken[");
    }

    private void putIfPresent(ObjectNode node, String key, String value) {
        String normalized = normalizeOptional(value);
        if (normalized != null) {
            node.put(key, normalized);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return Objects.requireNonNullElse(normalizeOptional(value), fallback);
    }
}
