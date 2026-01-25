package com.mandiconnect.controllers;

import com.mandiconnect.models.Notification;
import com.mandiconnect.repositories.NotificationRepository;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {


    private final NotificationRepository notificationRepository;
    private final JwtUtil jwtUtil;


    /* =====================================================
    1️⃣ GET ALL NOTIFICATIONS FOR USER
    ===================================================== */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserNotifications(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String userId
    ) {


        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }


        List<Notification> notifications =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);


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


        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }


        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return ResponseEntity.ok(count);
    }



}