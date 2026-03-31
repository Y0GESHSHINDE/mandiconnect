package com.mandiconnect.services;

import com.mandiconnect.models.NotificationDeviceToken;
import com.mandiconnect.repositories.NotificationDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationDeviceTokenService {

    private static final String DEFAULT_PROVIDER = "EXPO";

    private final NotificationDeviceTokenRepository notificationDeviceTokenRepository;

    public NotificationDeviceToken registerDeviceToken(RegisterDeviceTokenCommand command) {
        String userId = requireValue(command.userId(), "userId");
        String pushToken = requireValue(command.pushToken(), "pushToken");
        String provider = normalizeEnumLike(command.provider(), DEFAULT_PROVIDER);
        String platform = normalizeEnumLike(command.platform(), null);
        String deviceId = normalizeOptional(command.deviceId());
        String deviceName = normalizeOptional(command.deviceName());
        String appVersion = normalizeOptional(command.appVersion());
        LocalDateTime now = LocalDateTime.now();

        NotificationDeviceToken existing = notificationDeviceTokenRepository.findByPushToken(pushToken).orElse(null);
        if (existing != null) {
            existing.setUserId(userId);
            existing.setProvider(provider);
            existing.setPlatform(platform);
            existing.setDeviceId(deviceId);
            existing.setDeviceName(deviceName);
            existing.setAppVersion(appVersion);
            existing.setActive(true);
            existing.setLastSeenAt(now);
            existing.setUpdatedAt(now);
            deactivateSiblingDeviceTokens(userId, deviceId, existing.getId(), now);
            return notificationDeviceTokenRepository.save(existing);
        }

        NotificationDeviceToken created = NotificationDeviceToken.builder()
                .userId(userId)
                .pushToken(pushToken)
                .provider(provider)
                .platform(platform)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .appVersion(appVersion)
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .lastSeenAt(now)
                .build();

        deactivateSiblingDeviceTokens(userId, deviceId, null, now);
        return notificationDeviceTokenRepository.save(created);
    }

    public NotificationDeviceToken deactivateDeviceToken(DeactivateDeviceTokenCommand command) {
        String userId = requireValue(command.userId(), "userId");
        String pushToken = normalizeOptional(command.pushToken());
        String deviceId = normalizeOptional(command.deviceId());

        if (pushToken == null && deviceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pushToken or deviceId is required");
        }

        NotificationDeviceToken token = pushToken != null
                ? notificationDeviceTokenRepository.findByPushToken(pushToken).orElse(null)
                : findActiveByUserIdAndDeviceId(userId, deviceId);

        if (token == null || !Objects.equals(token.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device token not found");
        }

        if (!token.isActive()) {
            return token;
        }

        LocalDateTime now = LocalDateTime.now();
        token.setActive(false);
        token.setUpdatedAt(now);
        token.setLastSeenAt(now);
        return notificationDeviceTokenRepository.save(token);
    }

    public List<NotificationDeviceToken> getDeviceTokensForUser(String userId, boolean activeOnly) {
        String safeUserId = requireValue(userId, "userId");
        if (activeOnly) {
            return notificationDeviceTokenRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(safeUserId);
        }
        return notificationDeviceTokenRepository.findByUserIdOrderByUpdatedAtDesc(safeUserId);
    }

    private NotificationDeviceToken findActiveByUserIdAndDeviceId(String userId, String deviceId) {
        List<NotificationDeviceToken> matches =
                notificationDeviceTokenRepository.findByUserIdAndDeviceIdAndIsActiveTrue(userId, deviceId);

        return matches.isEmpty() ? null : matches.get(0);
    }

    private void deactivateSiblingDeviceTokens(String userId, String deviceId, String keepId, LocalDateTime now) {
        if (deviceId == null) {
            return;
        }

        List<NotificationDeviceToken> siblings =
                notificationDeviceTokenRepository.findByUserIdAndDeviceIdAndIsActiveTrue(userId, deviceId);

        boolean changed = false;
        for (NotificationDeviceToken sibling : siblings) {
            if (sibling == null || Objects.equals(sibling.getId(), keepId)) {
                continue;
            }
            sibling.setActive(false);
            sibling.setUpdatedAt(now);
            sibling.setLastSeenAt(now);
            changed = true;
        }

        if (changed) {
            notificationDeviceTokenRepository.saveAll(siblings);
        }
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEnumLike(String value, String fallback) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return fallback;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public record RegisterDeviceTokenCommand(
            String userId,
            String pushToken,
            String provider,
            String platform,
            String deviceId,
            String deviceName,
            String appVersion
    ) {
    }

    public record DeactivateDeviceTokenCommand(
            String userId,
            String pushToken,
            String deviceId
    ) {
    }
}
