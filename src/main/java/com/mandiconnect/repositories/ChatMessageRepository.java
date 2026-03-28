package com.mandiconnect.repositories;

import com.mandiconnect.models.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    Page<ChatMessage> findByChatIdOrderByCreatedAtDesc(String chatId, Pageable pageable);

    List<ChatMessage> findByChatIdAndSenderIdNotAndReadAtIsNull(String chatId, String senderId);
}
