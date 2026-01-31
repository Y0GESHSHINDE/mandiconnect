package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "connections")
public class Connection {

    @Id
    private String id;

    // Who initiated
    private String senderId;     // buyerId OR farmerId
    private String senderRole;   // BUYER / FARMER

    // Who receives
    private String receiverId;   // farmerId OR buyerId
    private String receiverRole; // FARMER / BUYER

    private String referenceType;
    private String referenceId;   // FarmerEntry OR BuyerDemand ID (optional)

    // REQUESTED / ACCEPTED / REJECTED / CLOSED
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
