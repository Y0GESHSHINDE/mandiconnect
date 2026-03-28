package com.mandiconnect.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mandiconnect.services.ChatService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/open")
    public ResponseEntity<?> openChat(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody OpenChatRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(chatService.openChat(body.connectionId(), email));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserChats(
            @PathVariable String userId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(chatService.getChatsForUser(userId, email));
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<?> getChatById(
            @PathVariable String chatId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(chatService.getChatById(chatId, email));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<?> getChatMessages(
            @PathVariable String chatId,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(chatService.getChatMessages(chatId, email, page, size));
    }

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<?> sendTextMessage(
            @PathVariable String chatId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SendTextMessageRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        ChatService.ChatDelivery delivery = chatService.sendTextMessage(
                chatId,
                email,
                body.text(),
                body.referenceType(),
                body.referenceId()
        );

        messagingTemplate.convertAndSend("/topic/chat/" + delivery.chat().getId(), delivery);
        return ResponseEntity.ok(delivery);
    }

    @PatchMapping("/{chatId}/read")
    public ResponseEntity<?> markChatAsRead(
            @PathVariable String chatId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        ChatService.ChatReadReceipt receipt = chatService.markChatAsRead(chatId, email);

        messagingTemplate.convertAndSend("/topic/chat/" + receipt.chat().getId() + "/read", receipt);
        return ResponseEntity.ok(receipt);
    }

    @PostMapping("/{chatId}/messages/image")
    public ResponseEntity<?> sendImageMessage(
            @PathVariable String chatId,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) String referenceId
    ) throws IOException {
        String email = extractAuthenticatedEmail(authHeader);
        ChatService.ChatDelivery delivery = chatService.sendImageMessage(
                chatId,
                email,
                file,
                text,
                referenceType,
                referenceId
        );

        messagingTemplate.convertAndSend("/topic/chat/" + delivery.chat().getId(), delivery);
        return ResponseEntity.ok(delivery);
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
    public record OpenChatRequest(String connectionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SendTextMessageRequest(
            String text,
            String referenceType,
            String referenceId
    ) {
    }
}
