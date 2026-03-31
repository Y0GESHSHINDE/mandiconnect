package com.mandiconnect.services;

import com.mandiconnect.repositories.BuyerRepository;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationAccessService {

    private final JwtUtil jwtUtil;
    private final BuyerRepository buyerRepository;
    private final FarmerRepository farmerRepository;

    public void assertUserAccess(String authHeader, String userId) {
        String token = extractToken(authHeader);
        if (token.isBlank() || !jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        String safeUserId = requireValue(userId, "userId");
        String email = jwtUtil.getEmailFromToken(token);
        List<String> allowedUserIds = resolveUserIdsByEmail(email);

        if (allowedUserIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found");
        }

        if (!allowedUserIds.contains(safeUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own notifications");
        }
    }

    private List<String> resolveUserIdsByEmail(String email) {
        String normalizedEmail = requireValue(email, "email").toLowerCase(Locale.ROOT);
        List<String> userIds = new ArrayList<>();

        buyerRepository.findByEmail(normalizedEmail)
                .ifPresent(buyer -> userIds.add(buyer.getId()));

        farmerRepository.findByEmail(normalizedEmail)
                .ifPresent(farmer -> userIds.add(farmer.getId()));

        return userIds;
    }

    private String extractToken(String authHeader) {
        if (authHeader == null) {
            return "";
        }
        return authHeader.replace("Bearer", "").trim();
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }
}
