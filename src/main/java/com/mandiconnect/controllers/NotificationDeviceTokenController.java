package com.mandiconnect.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mandiconnect.models.NotificationDeviceToken;
import com.mandiconnect.services.NotificationAccessService;
import com.mandiconnect.services.NotificationDeviceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications/device-tokens")
@RequiredArgsConstructor
public class NotificationDeviceTokenController {

    private final NotificationDeviceTokenService notificationDeviceTokenService;
    private final NotificationAccessService notificationAccessService;

    @PostMapping("/register")
    public ResponseEntity<?> registerDeviceToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody RegisterDeviceTokenRequest body
    ) {
        authorizeOrThrow(authHeader, body.userId());

        NotificationDeviceToken token = notificationDeviceTokenService.registerDeviceToken(
                new NotificationDeviceTokenService.RegisterDeviceTokenCommand(
                        body.userId(),
                        body.pushToken(),
                        body.provider(),
                        body.platform(),
                        body.deviceId(),
                        body.deviceName(),
                        body.appVersion()
                )
        );

        return ResponseEntity.ok(token);
    }

    @PostMapping("/deactivate")
    public ResponseEntity<?> deactivateDeviceToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody DeactivateDeviceTokenRequest body
    ) {
        authorizeOrThrow(authHeader, body.userId());

        NotificationDeviceToken token = notificationDeviceTokenService.deactivateDeviceToken(
                new NotificationDeviceTokenService.DeactivateDeviceTokenCommand(
                        body.userId(),
                        body.pushToken(),
                        body.deviceId()
                )
        );

        return ResponseEntity.ok(token);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserDeviceTokens(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly
    ) {
        authorizeOrThrow(authHeader, userId);

        List<NotificationDeviceToken> tokens =
                notificationDeviceTokenService.getDeviceTokensForUser(userId, activeOnly);
        return ResponseEntity.ok(tokens);
    }

    private void authorizeOrThrow(String authHeader, String userId) {
        try {
            notificationAccessService.assertUserAccess(authHeader, userId);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw ex;
            }
            throw ex;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegisterDeviceTokenRequest(
            String userId,
            String pushToken,
            String provider,
            String platform,
            String deviceId,
            String deviceName,
            String appVersion
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeactivateDeviceTokenRequest(
            String userId,
            String pushToken,
            String deviceId
    ) {
    }
}
