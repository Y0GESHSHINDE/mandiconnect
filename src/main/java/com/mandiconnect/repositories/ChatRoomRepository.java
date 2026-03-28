package com.mandiconnect.repositories;

import com.mandiconnect.models.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {

    Optional<ChatRoom> findByConnectionId(String connectionId);

    @Query(value = "{ 'participantKeys': ?0 }", sort = "{ 'updatedAt': -1 }")
    List<ChatRoom> findByParticipantKeyOrderByUpdatedAtDesc(String participantKey);
}
