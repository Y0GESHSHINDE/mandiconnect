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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "chat_rooms")
@CompoundIndexes({
        @CompoundIndex(name = "ux_chat_connection", def = "{'connectionId': 1}", unique = true),
        @CompoundIndex(name = "idx_chat_participant_updated", def = "{'participantKeys': 1, 'updatedAt': -1}")
})
public class ChatRoom {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Id
    private String id;

    private String connectionId;

    @Builder.Default
    private List<String> participantKeys = new ArrayList<>();

    @Builder.Default
    private List<ParticipantSnapshot> participants = new ArrayList<>();

    @Builder.Default
    private String status = ChatStatus.ACTIVE.name();

    private String lastMessageId;
    private String lastMessageText;
    private String lastMessageType;
    private String lastMessageSenderId;
    private LocalDateTime lastMessageAt;
    @Builder.Default
    private Integer unreadCountBuyer = 0;
    @Builder.Default
    private Integer unreadCountFarmer = 0;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(IST_ZONE);

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(IST_ZONE);

    public enum ChatStatus {
        ACTIVE,
        CLOSED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantSnapshot {
        private String userId;
        private String userType;
        private String displayName;
        private String city;
        private String state;
    }
}
