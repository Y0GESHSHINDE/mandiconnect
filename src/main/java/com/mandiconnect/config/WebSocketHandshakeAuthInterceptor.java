package com.mandiconnect.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WebSocketHandshakeAuthInterceptor implements HandshakeInterceptor {

    public static final String SESSION_AUTHORIZATION_KEY = "wsAuthorizationHeader";

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull org.springframework.http.server.ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        String authorizationHeader = request.getHeaders().getFirst("Authorization");
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            authorizationHeader = request.getHeaders().getFirst("authorization");
        }

        if ((authorizationHeader == null || authorizationHeader.isBlank())
                && request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            authorizationHeader = resolveTokenFromQuery(httpServletRequest);
        }

        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            attributes.put(SESSION_AUTHORIZATION_KEY, authorizationHeader);
        }

        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull org.springframework.http.server.ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @Nullable Exception exception
    ) {
        if (exception != null) {
            log.debug("WebSocket handshake completed with exception", exception);
        }
    }

    private String resolveTokenFromQuery(HttpServletRequest request) {
        String bearerToken = normalizeQueryToken(request.getParameter("token"));
        if (bearerToken != null) {
            return bearerToken;
        }

        return normalizeQueryToken(request.getParameter("access_token"));
    }

    private String normalizeQueryToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        if (token.startsWith("Bearer ")) {
            return token;
        }

        return "Bearer " + token.trim();
    }
}
