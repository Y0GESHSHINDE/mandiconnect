package com.mandiconnect.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mandiconnect.services.ChatService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatSendMessageRequest request, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            log.warn("Rejected websocket chat.send because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized websocket session");
        }

        log.info("Received websocket chat.send for chatId={} from={}", request.chatId(), principal.getName());

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
