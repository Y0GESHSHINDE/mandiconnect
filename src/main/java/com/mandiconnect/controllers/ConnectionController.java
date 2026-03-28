package com.mandiconnect.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mandiconnect.models.Connection;
import com.mandiconnect.services.ConnectionService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private final JwtUtil jwtUtil;

    @PostMapping("/send")
    public ResponseEntity<?> sendRequest(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SendConnectionRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);

        Connection connection = connectionService.sendRequest(
                email,
                body.senderId(),
                body.senderRole(),
                body.receiverId(),
                body.receiverRole(),
                body.referenceType(),
                body.referenceId()
        );

        return ResponseEntity.ok(connection);
    }

    @PostMapping("/accept/{connectionId}")
    public ResponseEntity<?> accept(
            @PathVariable String connectionId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(connectionService.acceptRequest(connectionId, email));
    }

    @PostMapping("/reject/{connectionId}")
    public ResponseEntity<?> reject(
            @PathVariable String connectionId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(connectionService.rejectRequest(connectionId, email));
    }

    @GetMapping("/incoming/{userId}")
    public ResponseEntity<?> incoming(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(connectionService.getIncomingRequests(userId, email));
    }

    @GetMapping("/sent/{userId}")
    public ResponseEntity<?> sent(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(connectionService.getSentRequests(userId, email));
    }

    @GetMapping("/all/{userId}")
    public ResponseEntity<?> all(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(connectionService.getAllConnections(userId, email));
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<?> getById(
            @PathVariable String connectionId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(connectionService.getConnectionById(connectionId, email));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String senderId,
            @RequestParam String senderRole,
            @RequestParam String otherUserId,
            @RequestParam String otherUserRole
    ) {
        String email = extractAuthenticatedEmail(authHeader);

        return connectionService.getConnectionStatus(email, senderId, senderRole, otherUserId, otherUserRole)
                .map(connection -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("exists", true);
                    response.put("connectionId", connection.getId());
                    response.put("status", connection.getNormalizedStatus());
                    response.put("pairKey", connection.getPairKey());
                    response.put("connection", connection);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("exists", false);
                    response.put("connectionId", null);
                    response.put("status", "NONE");
                    response.put("pairKey", null);
                    response.put("connection", null);
                    return ResponseEntity.ok(response);
                });
    }

    private String extractAuthenticatedEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        return jwtUtil.getEmailFromToken(token);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SendConnectionRequest(
            String senderId,
            String senderRole,
            String receiverId,
            String receiverRole,
            String referenceType,
            String referenceId
    ) {
    }
}
