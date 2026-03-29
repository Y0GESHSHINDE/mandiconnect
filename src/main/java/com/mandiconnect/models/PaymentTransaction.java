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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "payment_transactions")
@CompoundIndexes({
        @CompoundIndex(name = "idx_payment_order_created", def = "{'orderId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_payment_buyer_created", def = "{'buyerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_payment_farmer_created", def = "{'farmerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "ux_payment_gateway_order", def = "{'gatewayOrderId': 1}", unique = true, sparse = true),
        @CompoundIndex(name = "ux_payment_gateway_payment", def = "{'gatewayPaymentId': 1}", unique = true, sparse = true)
})
public class PaymentTransaction {

    @Id
    private String id;

    private String orderId;
    private String orderCode;
    private String connectionId;
    private String chatId;
    private String buyerId;
    private String farmerId;

    @Builder.Default
    private String gateway = Gateway.RAZORPAY.name();

    private Double amount;
    private Long amountInSubunits;

    @Builder.Default
    private String currency = "INR";

    @Builder.Default
    private String status = TransactionStatus.INITIATED.name();

    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String gatewaySignature;
    private String gatewayStatus;
    private String receipt;
    private String failureCode;
    private String failureDescription;

    @Builder.Default
    private LocalDateTime initiatedAt = LocalDateTime.now();

    private LocalDateTime verifiedAt;
    private LocalDateTime paidAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Gateway {
        RAZORPAY
    }

    public enum TransactionStatus {
        INITIATED,
        SUCCESS,
        FAILED,
        CANCELLED,
        REFUNDED
    }
}
