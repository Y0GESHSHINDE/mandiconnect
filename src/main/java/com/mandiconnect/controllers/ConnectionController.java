package com.mandiconnect.controllers;

import com.mandiconnect.models.Connection;
import com.mandiconnect.services.ConnectionService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private final JwtUtil jwtUtil;

    // Send connection request
    @PostMapping("/send")
    public ResponseEntity<?> sendRequest(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body
    ) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(401).body("UNAUTHORIZED");
        }

        Connection connection = connectionService.sendRequest(
                body.get("senderId"),
                body.get("senderRole"),
                body.get("receiverId"),
                body.get("receiverRole"),
                body.get("referenceType"),
                body.get("referenceId")
        );

        return ResponseEntity.ok(connection);
    }

    // Accept connection
    @PostMapping("/accept/{connectionId}")
    public ResponseEntity<?> accept(@PathVariable String connectionId) {
        return ResponseEntity.ok(connectionService.acceptRequest(connectionId));
    }

    // Reject connection
    @PostMapping("/reject/{connectionId}")
    public ResponseEntity<?> reject(@PathVariable String connectionId) {
        return ResponseEntity.ok(connectionService.rejectRequest(connectionId));
    }

    // Get incoming requests
    @GetMapping("/incoming/{userId}")
    public ResponseEntity<List<Connection>> incoming(@PathVariable String userId) {
        return ResponseEntity.ok(connectionService.getIncomingRequests(userId));
    }

    @GetMapping("/sent/{userId}")
    public ResponseEntity<?> sent(@RequestHeader("Authorization") String auth , @PathVariable String userId ) {
        return ResponseEntity.ok(
                connectionService.getSentRequests(userId)
        );
    }

}
