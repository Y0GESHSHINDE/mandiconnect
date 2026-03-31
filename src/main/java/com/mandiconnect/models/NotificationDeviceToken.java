package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notification_device_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "ux_notification_push_token", def = "{'pushToken': 1}", unique = true),
        @CompoundIndex(name = "idx_notification_user_active_updated", def = "{'userId': 1, 'isActive': 1, 'updatedAt': -1}")
})
public class NotificationDeviceToken {

    @Id
    private String id;

    private String userId;
    private String pushToken;
    private String provider;
    private String platform;
    private String deviceId;
    private String deviceName;
    private String appVersion;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime lastSeenAt = LocalDateTime.now();
}
