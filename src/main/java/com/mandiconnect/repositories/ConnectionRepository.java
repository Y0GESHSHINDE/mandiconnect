package com.mandiconnect.repositories;

import com.mandiconnect.models.Connection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConnectionRepository extends MongoRepository<Connection, String> {

    List<Connection> findByReceiverIdAndStatus(String receiverId, String status);

    List<Connection> findBySenderId(String senderId);

    List<Connection> findBySenderIdOrderByCreatedAtDesc(String senderId);
}
