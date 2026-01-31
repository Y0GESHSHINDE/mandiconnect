package com.mandiconnect.services;

import com.mandiconnect.models.Connection;
import com.mandiconnect.repositories.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final ConnectionRepository connectionRepository;

    // Buyer/Farmer sends request
    public Connection sendRequest(
            String senderId,
            String senderRole,
            String receiverId,
            String receiverRole,
            String referenceType,
            String referenceId
    ) {
        Connection connection = Connection.builder()
                .senderId(senderId)
                .senderRole(senderRole)
                .receiverId(receiverId)
                .receiverRole(receiverRole)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .status("REQUESTED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return connectionRepository.save(connection);
    }

    // Accept request
    public Connection acceptRequest(String connectionId) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connection not found"));

        connection.setStatus("ACCEPTED");
        connection.setUpdatedAt(LocalDateTime.now());

        return connectionRepository.save(connection);
    }

    // Reject request
    public Connection rejectRequest(String connectionId) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connection not found"));

        connection.setStatus("REJECTED");
        connection.setUpdatedAt(LocalDateTime.now());

        return connectionRepository.save(connection);
    }

    // Incoming requests
    public List<Connection> getIncomingRequests(String userId) {
        return connectionRepository.findByReceiverIdAndStatus(userId, "REQUESTED");
    }

//       5️⃣ GET MY SENT REQUESTS ✅
//            ================================ */
    public List<Connection> getSentRequests(String userId) {
        return connectionRepository
                .findBySenderIdOrderByCreatedAtDesc(userId);
    }
}
