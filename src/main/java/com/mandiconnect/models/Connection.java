package com.mandiconnect.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "connections")
@CompoundIndexes({
        @CompoundIndex(name = "ux_connection_pair", def = "{'pairKey': 1}", unique = true, sparse = true),
        @CompoundIndex(name = "idx_connection_participant_status", def = "{'participantKeys': 1, 'status': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_connection_requested_by", def = "{'requestedByUserId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_connection_context", def = "{'contexts.type': 1, 'contexts.refId': 1}")
})
public class Connection {

    @Id
    private String id;

    private String pairKey;

    @Builder.Default
    private List<String> participantKeys = new ArrayList<>();

    @Builder.Default
    private List<ParticipantSnapshot> participants = new ArrayList<>();

    private String senderId;
    private String senderRole;
    private String receiverId;
    private String receiverRole;
    private String referenceType;
    private String referenceId;

    @Builder.Default
    private String status = ConnectionStatus.PENDING.name();

    private String requestedByUserId;
    private UserType requestedByUserType;
    private String actionByUserId;

    @Builder.Default
    private List<ContextRef> contexts = new ArrayList<>();

    private String chatId;

    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime respondedAt;
    private LocalDateTime connectedAt;

    public static String buildParticipantKey(String userId, String userType) {
        return normalize(userType) + ":" + requireValue(userId, "userId");
    }

    public static String buildPairKey(String firstUserId, String firstUserType, String secondUserId, String secondUserType) {
        return List.of(
                        buildParticipantKey(firstUserId, firstUserType),
                        buildParticipantKey(secondUserId, secondUserType)
                ).stream()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    public void addContext(ContextRef context) {
        if (context == null || context.getType() == null || context.getRefId() == null || context.getRefId().isBlank()) {
            return;
        }

        boolean exists = this.contexts.stream()
                .anyMatch(existing ->
                        existing.getType() == context.getType()
                                && existing.getRefId().equals(context.getRefId())
                );

        if (!exists) {
            this.contexts.add(context);
        }
    }

    public String getSenderId() {
        return requestedByUserId != null ? requestedByUserId : senderId;
    }

    public String getSenderRole() {
        return requestedByUserType != null ? requestedByUserType.name() : senderRole;
    }

    public String getReceiverId() {
        String calculatedReceiverId = participants.stream()
                .filter(participant -> !Objects.equals(participant.getUserId(), requestedByUserId))
                .map(ParticipantSnapshot::getUserId)
                .findFirst()
                .orElse(null);
        return calculatedReceiverId != null ? calculatedReceiverId : receiverId;
    }

    public String getReceiverRole() {
        String calculatedReceiverRole = participants.stream()
                .filter(participant -> !Objects.equals(participant.getUserId(), requestedByUserId))
                .map(ParticipantSnapshot::getUserType)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .findFirst()
                .orElse(null);
        return calculatedReceiverRole != null ? calculatedReceiverRole : receiverRole;
    }

    public String getReferenceType() {
        if (!contexts.isEmpty() && contexts.get(0).getType() != null) {
            return contexts.get(0).getType().name();
        }
        return referenceType;
    }

    public String getReferenceId() {
        if (!contexts.isEmpty()) {
            return contexts.get(0).getRefId();
        }
        return referenceId;
    }

    public String getNormalizedStatus() {
        if (status == null || status.isBlank()) {
            return ConnectionStatus.PENDING.name();
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("REQUESTED".equals(normalized)) {
            return ConnectionStatus.PENDING.name();
        }
        return normalized;
    }

    public void normalizeStatus() {
        this.status = getNormalizedStatus();
    }

    public void syncLegacyFields() {
        this.senderId = requestedByUserId;
        this.senderRole = requestedByUserType != null ? requestedByUserType.name() : this.senderRole;
        this.receiverId = getReceiverId();
        this.receiverRole = getReceiverRole();
        this.referenceType = getReferenceType();
        this.referenceId = getReferenceId();
        normalizeStatus();
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return requireValue(value, "userType").trim().toUpperCase(Locale.ROOT);
    }

    public enum ConnectionStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        CLOSED;

        public static ConnectionStatus from(String value) {
            return ConnectionStatus.valueOf(normalize(value));
        }
    }

    public enum UserType {
        BUYER,
        FARMER;

        public static UserType from(String value) {
            return UserType.valueOf(normalize(value));
        }
    }

    public enum ContextType {
        CROP,
        DEMAND;

        public static ContextType from(String value) {
            String normalized = normalize(value);
            if ("LISTING".equals(normalized) || "CROP_LISTING".equals(normalized)) {
                return CROP;
            }
            if ("BUYER_DEMAND".equals(normalized)) {
                return DEMAND;
            }
            return ContextType.valueOf(normalized);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantSnapshot {
        private String userId;
        private UserType userType;
        private String displayName;
        private String city;
        private String state;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextRef {
        private ContextType type;
        private String refId;
        private String cropId;

        @Builder.Default
        private LocalDateTime addedAt = LocalDateTime.now();
    }
}
