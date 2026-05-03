package com.mandiconnect.repositories;

import com.mandiconnect.models.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository  extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, String type);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(String userId, String type);

    Optional<Notification> findByIdAndUserId(String id, String userId);

    long countByUserIdAndIsReadFalse(String userId);

    long deleteByUserId(String userId);
}
