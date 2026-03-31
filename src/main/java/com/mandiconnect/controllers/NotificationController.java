package com.mandiconnect.controllers;

import com.mandiconnect.models.Notification;
import com.mandiconnect.repositories.NotificationRepository;
import com.mandiconnect.services.NotificationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {


    private final NotificationRepository notificationRepository;
    private final NotificationAccessService notificationAccessService;


    /* =====================================================
    1️⃣ GET ALL NOTIFICATIONS FOR USER
    ===================================================== */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserNotifications(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean unreadOnly
    ) {
        authorizeOrThrow(authHeader, userId);

        List<Notification> notifications = findNotifications(userId, type, unreadOnly);
        return ResponseEntity.ok(notifications);
    }


    /* =====================================================
    2️⃣ GET UNREAD COUNT (badge)
    ===================================================== */
    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String userId
    ) {
        authorizeOrThrow(authHeader, userId);
        
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return ResponseEntity.ok(count);
    }

    @PatchMapping("/user/{userId}/{notificationId}/read")
    public ResponseEntity<?> markAsRead(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String userId,
            @PathVariable String notificationId
    ) {
        authorizeOrThrow(authHeader, userId);

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElse(null);

        if (notification == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Notification not found");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }

        return ResponseEntity.ok(notification);
    }

    @PatchMapping("/user/{userId}/read-all")
    public ResponseEntity<?> markAllAsRead(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String userId
    ) {
        authorizeOrThrow(authHeader, userId);

        List<Notification> unreadNotifications =
                notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);

        if (!unreadNotifications.isEmpty()) {
            unreadNotifications.forEach(notification -> notification.setRead(true));
            notificationRepository.saveAll(unreadNotifications);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("markedCount", unreadNotifications.size());
        response.put("unreadCount", 0);
        response.put("updatedAt", LocalDateTime.now());

        return ResponseEntity.ok(response);
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

    private List<Notification> findNotifications(String userId, String type, Boolean unreadOnly) {
        String normalizedType = normalizeType(type);
        boolean unreadFilter = Boolean.TRUE.equals(unreadOnly);

        if (!normalizedType.isBlank() && unreadFilter) {
            return notificationRepository.findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(
                    userId,
                    normalizedType
            );
        }

        if (!normalizedType.isBlank()) {
            return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, normalizedType);
        }

        if (unreadFilter) {
            return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        }

        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toUpperCase();
    }


}
