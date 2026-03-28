package com.mandiconnect.services;

import com.mandiconnect.models.Farmer;
import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.models.Notification;
import com.mandiconnect.models.Connection;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FarmerRepository farmerRepository;

    /* =====================================================
       1️⃣ PRICE POSTED (Broadcast)
       ===================================================== */
    public void notifyPricePosted(FarmerEntry entry) {

        String cropId = entry.getCrop().getId();
        String marketId = entry.getMarket().getId();

        List<Farmer> farmers = farmerRepository.findAll();

        for (Farmer farmer : farmers) {

            // skip self
            if (farmer.getId().equals(entry.getFarmer().getId())) {
                continue;
            }

            if (!isInterestedFarmer(farmer, cropId, marketId)) {
                continue;
            }

            Notification notification = Notification.builder()
                    .userId(farmer.getId())
                    .type("PRICE_POSTED")
                    .title("New Price Posted")
                    .message(
                            "New price for "
                                    + entry.getCrop().getName()
                                    + " posted in "
                                    + entry.getMarket().getMarketName()
                    )
                    .cropId(cropId)
                    .marketId(marketId)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepository.save(notification);
        }
    }

    /* =====================================================
       2️⃣ PRICE AGREE
       ===================================================== */
    public void notifyPriceAgree(FarmerEntry entry, String voterFarmerId) {

        // do not notify if farmer votes on own price
        if (entry.getFarmer().getId().equals(voterFarmerId)) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(entry.getFarmer().getId()) // ✅ RECEIVER
                .type("PRICE_AGREE")
                .title("Price Approved")
                .message(
                        "A farmer agreed with your "
                                + entry.getCrop().getName()
                                + " price in "
                                + entry.getMarket().getMarketName()
                )
                .cropId(entry.getCrop().getId())
                .marketId(entry.getMarket().getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    /* =====================================================
       3️⃣ PRICE DISAGREE
       ===================================================== */
    public void notifyPriceDisAgree(FarmerEntry entry, String voterFarmerId) {

        // do not notify if farmer votes on own price
        if (entry.getFarmer().getId().equals(voterFarmerId)) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(entry.getFarmer().getId()) // ✅ RECEIVER
                .type("PRICE_DISAGREE")
                .title("Price Disagreed")
                .message(
                        "A farmer disagreed with your "
                                + entry.getCrop().getName()
                                + " price in "
                                + entry.getMarket().getMarketName()
                )
                .cropId(entry.getCrop().getId())
                .marketId(entry.getMarket().getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    public void notifyConnectionRequestReceived(Connection connection) {
        Connection.ParticipantSnapshot requester = findRequester(connection);
        Connection.ParticipantSnapshot receiver = findReceiver(connection);

        if (requester == null || receiver == null || receiver.getUserId() == null || receiver.getUserId().isBlank()) {
            return;
        }

        saveConnectionNotification(
                receiver.getUserId(),
                "CONNECTION_REQUEST_RECEIVED",
                "New Connection Request",
                buildActorName(requester) + " sent you a connection request for " + buildContextLabel(connection),
                connection,
                requester
        );
    }

    public void notifyConnectionAccepted(Connection connection) {
        Connection.ParticipantSnapshot requester = findRequester(connection);
        Connection.ParticipantSnapshot actor = findActor(connection);

        if (requester == null || requester.getUserId() == null || requester.getUserId().isBlank()) {
            return;
        }

        saveConnectionNotification(
                requester.getUserId(),
                "CONNECTION_ACCEPTED",
                "Connection Request Accepted",
                buildActorName(actor) + " accepted your connection request for " + buildContextLabel(connection),
                connection,
                actor
        );
    }

    public void notifyConnectionRejected(Connection connection) {
        Connection.ParticipantSnapshot requester = findRequester(connection);
        Connection.ParticipantSnapshot actor = findActor(connection);

        if (requester == null || requester.getUserId() == null || requester.getUserId().isBlank()) {
            return;
        }

        saveConnectionNotification(
                requester.getUserId(),
                "CONNECTION_REJECTED",
                "Connection Request Rejected",
                buildActorName(actor) + " rejected your connection request for " + buildContextLabel(connection),
                connection,
                actor
        );
    }

    /* =====================================================
       Helper
       ===================================================== */
    private boolean isInterestedFarmer(Farmer farmer, String cropId, String marketId) {

        if (farmer.getFarmDetails() == null) return false;
        if (farmer.getFarmDetails().getCropIds() == null) return false;
        if (farmer.getFarmDetails().getPreferredMarketIds() == null) return false;

        return farmer.getFarmDetails().getCropIds().contains(cropId)
                && farmer.getFarmDetails().getPreferredMarketIds().contains(marketId);
    }

    private void saveConnectionNotification(
            String userId,
            String type,
            String title,
            String message,
            Connection connection,
            Connection.ParticipantSnapshot actor
    ) {
        Connection.ContextRef primaryContext = getPrimaryContext(connection);

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .cropId(primaryContext != null ? primaryContext.getCropId() : null)
                .connectionId(connection.getId())
                .referenceType(primaryContext != null && primaryContext.getType() != null ? primaryContext.getType().name() : null)
                .referenceId(primaryContext != null ? primaryContext.getRefId() : null)
                .actorUserId(actor != null ? actor.getUserId() : null)
                .actorUserRole(actor != null && actor.getUserType() != null ? actor.getUserType().name() : null)
                .actorName(actor != null ? actor.getDisplayName() : null)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    private Connection.ParticipantSnapshot findRequester(Connection connection) {
        if (connection.getRequestedByUserId() == null) {
            return null;
        }

        return connection.getParticipants().stream()
                .filter(participant -> connection.getRequestedByUserId().equals(participant.getUserId()))
                .findFirst()
                .orElse(null);
    }

    private Connection.ParticipantSnapshot findReceiver(Connection connection) {
        if (connection.getRequestedByUserId() == null) {
            return null;
        }

        return connection.getParticipants().stream()
                .filter(participant -> !connection.getRequestedByUserId().equals(participant.getUserId()))
                .findFirst()
                .orElse(null);
    }

    private Connection.ParticipantSnapshot findActor(Connection connection) {
        if (connection.getActionByUserId() == null || connection.getActionByUserId().isBlank()) {
            return findReceiver(connection);
        }

        return connection.getParticipants().stream()
                .filter(participant -> connection.getActionByUserId().equals(participant.getUserId()))
                .findFirst()
                .orElse(findReceiver(connection));
    }

    private Connection.ContextRef getPrimaryContext(Connection connection) {
        if (connection.getContexts() == null || connection.getContexts().isEmpty()) {
            return null;
        }
        return connection.getContexts().get(0);
    }

    private String buildActorName(Connection.ParticipantSnapshot actor) {
        if (actor == null || actor.getDisplayName() == null || actor.getDisplayName().isBlank()) {
            return "Someone";
        }
        return actor.getDisplayName();
    }

    private String buildContextLabel(Connection connection) {
        Connection.ContextRef primaryContext = getPrimaryContext(connection);
        if (primaryContext == null) {
            return "your listing";
        }

        if (primaryContext.getTitle() != null && !primaryContext.getTitle().isBlank()) {
            return primaryContext.getTitle();
        }

        if (primaryContext.getCropName() != null && !primaryContext.getCropName().isBlank()) {
            return primaryContext.getCropName();
        }

        return primaryContext.getType() == Connection.ContextType.DEMAND ? "your demand" : "your crop";
    }
}
