package com.mandiconnect.config;

import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String SESSION_EMAIL_KEY = "authenticatedEmail";

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            String email = extractEmail(accessor);
            accessor.setUser(new StompPrincipal(email));
            accessor.getSessionAttributes().put(SESSION_EMAIL_KEY, email);
            return message;
        }

        Principal user = accessor.getUser();
        if (user == null) {
            Object sessionEmail = accessor.getSessionAttributes() != null
                    ? accessor.getSessionAttributes().get(SESSION_EMAIL_KEY)
                    : null;
            if (sessionEmail instanceof String email && !email.isBlank()) {
                accessor.setUser(new StompPrincipal(email));
            }
        }

        if (accessor.getUser() == null
                && (StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized websocket session");
        }

        return message;
    }

    private String extractEmail(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing websocket Authorization header");
        }

        String rawHeader = authorizationHeaders.get(0);
        if (rawHeader == null || !rawHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid websocket Authorization header");
        }

        String token = rawHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid websocket token");
        }

        return jwtUtil.getEmailFromToken(token);
    }
}
