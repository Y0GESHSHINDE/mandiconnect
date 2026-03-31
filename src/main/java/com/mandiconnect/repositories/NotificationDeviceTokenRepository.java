package com.mandiconnect.repositories;

import com.mandiconnect.models.NotificationDeviceToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationDeviceTokenRepository extends MongoRepository<NotificationDeviceToken, String> {

    Optional<NotificationDeviceToken> findByPushToken(String pushToken);

    List<NotificationDeviceToken> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<NotificationDeviceToken> findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(String userId);

    List<NotificationDeviceToken> findByUserIdAndDeviceIdAndIsActiveTrue(String userId, String deviceId);
}
