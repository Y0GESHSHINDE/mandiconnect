package com.mandiconnect.repositories;

import com.mandiconnect.models.Connection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends MongoRepository<Connection, String> {

    Optional<Connection> findByPairKey(String pairKey);

    Optional<Connection> findFirstBySenderIdAndReceiverIdOrSenderIdAndReceiverId(
            String senderId,
            String receiverId,
            String reverseSenderId,
            String reverseReceiverId
    );

    List<Connection> findByReceiverIdAndStatusOrderByCreatedAtDesc(String receiverId, String status);

    List<Connection> findBySenderIdOrderByCreatedAtDesc(String senderId);
}
