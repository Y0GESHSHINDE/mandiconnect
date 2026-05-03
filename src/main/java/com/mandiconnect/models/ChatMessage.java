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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "chat_messages")
@CompoundIndexes({
        @CompoundIndex(name = "idx_chat_message_room_created", def = "{'chatId': 1, 'createdAt': 1}"),
        @CompoundIndex(name = "idx_chat_message_connection_created", def = "{'connectionId': 1, 'createdAt': 1}")
})
public class ChatMessage {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Id
    private String id;

    private String chatId;
    private String connectionId;
    private String senderId;
    private String senderRole;

    @Builder.Default
    private String type = MessageType.TEXT.name();

    private String text;
    private String referenceType;
    private String referenceId;
    private String mediaUrl;
    private String mediaPublicId;
    private LocalDateTime readAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(IST_ZONE);

    public enum MessageType {
        TEXT,
        IMAGE,
        SYSTEM
    }
}
