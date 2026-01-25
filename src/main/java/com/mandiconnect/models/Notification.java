package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    private String userId;     // farmerId
    private String type;       // PRICE_POSTED | AVG_PRICE_CHANGED | NO_PRICE_UPDATE

    private String title;
    private String message;

    private String cropId;
    private String marketId;

    private boolean isRead = false;
    private LocalDateTime createdAt;
}