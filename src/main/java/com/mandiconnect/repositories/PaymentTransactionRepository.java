package com.mandiconnect.repositories;

import com.mandiconnect.models.PaymentTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, String> {

    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<PaymentTransaction> findTopByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<PaymentTransaction> findByGatewayOrderId(String gatewayOrderId);

    Optional<PaymentTransaction> findByGatewayPaymentId(String gatewayPaymentId);
}
