package com.mandiconnect.repositories;

import com.mandiconnect.models.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<Order> findByFarmerIdOrderByCreatedAtDesc(String farmerId);

    List<Order> findByConnectionIdOrderByCreatedAtDesc(String connectionId);

    List<Order> findByChatIdOrderByCreatedAtDesc(String chatId);

    Optional<Order> findByOrderCode(String orderCode);
}
