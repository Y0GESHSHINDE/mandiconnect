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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "orders")
@CompoundIndexes({
        @CompoundIndex(name = "ux_order_code", def = "{'orderCode': 1}", unique = true),
        @CompoundIndex(name = "idx_order_buyer_created", def = "{'buyerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_order_farmer_created", def = "{'farmerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_order_connection_created", def = "{'connectionId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_order_chat_created", def = "{'chatId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_order_context_created", def = "{'contextType': 1, 'contextRefId': 1, 'createdAt': -1}")
})
public class Order {

    @Id
    private String id;

    private String orderCode;
    private String connectionId;
    private String chatId;

    @Builder.Default
    private ContextType contextType = ContextType.CROP;

    private String contextRefId;
    private String buyerId;
    private String farmerId;

    private PartySnapshot buyer;
    private PartySnapshot farmer;
    private OrderItem item;
    private DeliveryDetails deliveryDetails;

    private String notes;

    private Double subtotalAmount;
    private Double totalAmount;

    @Builder.Default
    private String currency = "INR";

    @Builder.Default
    private String status = OrderStatus.PLACED.name();

    @Builder.Default
    private String paymentStatus = PaymentStatus.PENDING.name();

    @Builder.Default
    private String deliveryStatus = DeliveryStatus.PENDING.name();

    @Builder.Default
    private List<OrderEvent> events = new ArrayList<>();

    @Builder.Default
    private LocalDateTime placedAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum ContextType {
        CROP
    }

    public enum OrderStatus {
        DRAFT,
        PLACED,
        PAYMENT_PENDING,
        PAID,
        CONFIRMED,
        PROCESSING,
        DISPATCHED,
        DELIVERED,
        COMPLETED,
        CANCELLED
    }

    public enum PaymentStatus {
        PENDING,
        INITIATED,
        SUCCESS,
        FAILED,
        CANCELLED,
        REFUNDED
    }

    public enum DeliveryStatus {
        PENDING,
        SCHEDULED,
        PACKED,
        OUT_FOR_DELIVERY,
        DELIVERED,
        FAILED,
        CANCELLED
    }

    public enum EventType {
        ORDER_PLACED,
        PAYMENT_PENDING,
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        ORDER_CONFIRMED,
        ORDER_PROCESSING,
        ORDER_DISPATCHED,
        ORDER_DELIVERED,
        ORDER_COMPLETED,
        ORDER_CANCELLED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartySnapshot {
        private String userId;
        private String userType;
        private String displayName;
        private String mobile;
        private String city;
        private String state;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItem {
        private String cropListingId;
        private String cropId;
        private String cropName;
        private String cropVariety;
        private String title;
        private String photoUrl;
        private String locationCity;
        private String locationState;
        private Double listedQuantity;
        private String listedUnit;
        private Double listedPrice;
        private Double orderedQuantity;
        private String orderedUnit;
        private Double agreedPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeliveryDetails {
        private String contactName;
        private String contactPhone;
        private String addressLine1;
        private String addressLine2;
        private String village;
        private String city;
        private String state;
        private String country;
        private String pincode;
        private String landmark;
        private LocalDate preferredDeliveryDate;
        private String preferredTimeSlot;
        private String instructions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderEvent {
        private EventType type;
        private String orderStatus;
        private String paymentStatus;
        private String deliveryStatus;
        private String actionByUserId;
        private String actionByUserType;
        private String note;

        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();
    }
}
