package com.mandiconnect.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mandiconnect.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatSendMessageRequest request, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized websocket session");
        }

        ChatService.ChatDelivery delivery = chatService.sendTextMessage(
                request.chatId(),
                principal.getName(),
                request.text(),
                request.referenceType(),
                request.referenceId()
        );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + delivery.chat().getId(),
                delivery
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatSendMessageRequest(
            String chatId,
            String text,
            String referenceType,
            String referenceId
    ) {
    }
}
