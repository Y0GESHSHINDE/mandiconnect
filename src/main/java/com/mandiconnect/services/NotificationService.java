package com.mandiconnect.services;

import com.mandiconnect.models.Farmer;
import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.models.Notification;
import com.mandiconnect.models.Connection;
import com.mandiconnect.models.Order;
import com.mandiconnect.models.PaymentTransaction;
import com.mandiconnect.models.ChatMessage;
import com.mandiconnect.models.ChatRoom;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FarmerRepository farmerRepository;
    private final NotificationPushService notificationPushService;

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

            saveNotification(notification);
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

        saveNotification(notification);
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

        saveNotification(notification);
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

    public void notifyOrderPlaced(Order order) {
        saveOrderNotification(
                order.getFarmerId(),
                "ORDER_PLACED",
                "New Order Placed",
                buildPartyName(order.getBuyer()) + " placed " + buildOrderLabel(order),
                order,
                order.getBuyer(),
                null
        );
    }

    public void notifyPaymentSuccess(Order order, PaymentTransaction payment) {
        saveOrderNotification(
                order.getBuyerId(),
                "PAYMENT_SUCCESS",
                "Payment Successful",
                "Payment successful for " + buildOrderLabel(order),
                order,
                order.getBuyer(),
                payment
        );

        saveOrderNotification(
                order.getFarmerId(),
                "ORDER_PAID",
                "Order Paid",
                buildPartyName(order.getBuyer()) + " completed payment for " + buildOrderLabel(order),
                order,
                order.getBuyer(),
                payment
        );
    }

    public void notifyPaymentFailed(Order order, PaymentTransaction payment) {
        saveOrderNotification(
                order.getBuyerId(),
                "PAYMENT_FAILED",
                "Payment Failed",
                "Payment failed for " + buildOrderLabel(order),
                order,
                order.getBuyer(),
                payment
        );
    }

    public void notifyOrderConfirmed(Order order) {
        saveOrderNotification(
                order.getBuyerId(),
                "ORDER_CONFIRMED",
                "Order Confirmed",
                buildPartyName(order.getFarmer()) + " confirmed " + buildOrderLabel(order),
                order,
                order.getFarmer(),
                null
        );
    }

    public void notifyOrderProcessing(Order order) {
        saveOrderNotification(
                order.getBuyerId(),
                "ORDER_PROCESSING",
                "Order Processing",
                buildPartyName(order.getFarmer()) + " started processing " + buildOrderLabel(order),
                order,
                order.getFarmer(),
                null
        );
    }

    public void notifyOrderDispatched(Order order) {
        saveOrderNotification(
                order.getBuyerId(),
                "ORDER_DISPATCHED",
                "Order Dispatched",
                buildPartyName(order.getFarmer()) + " dispatched " + buildOrderLabel(order),
                order,
                order.getFarmer(),
                null
        );
    }

    public void notifyOrderDelivered(Order order) {
        saveOrderNotification(
                order.getBuyerId(),
                "ORDER_DELIVERED",
                "Order Delivered",
                buildPartyName(order.getFarmer()) + " marked " + buildOrderLabel(order) + " as delivered",
                order,
                order.getFarmer(),
                null
        );
    }

    public void notifyOrderCompleted(Order order) {
        saveOrderNotification(
                order.getFarmerId(),
                "ORDER_COMPLETED",
                "Order Completed",
                buildPartyName(order.getBuyer()) + " completed " + buildOrderLabel(order),
                order,
                order.getBuyer(),
                null
        );
    }

    public void notifyOrderCancelled(Order order) {
        Order.OrderEvent latestEvent = getLatestOrderEvent(order);
        String actorUserId = latestEvent != null ? latestEvent.getActionByUserId() : null;
        String targetUserId = actorUserId != null && actorUserId.equals(order.getBuyerId()) ? order.getFarmerId() : order.getBuyerId();
        Order.PartySnapshot actor = actorUserId != null && actorUserId.equals(order.getBuyerId()) ? order.getBuyer() : order.getFarmer();

        saveOrderNotification(
                targetUserId,
                "ORDER_CANCELLED",
                "Order Cancelled",
                buildPartyName(actor) + " cancelled " + buildOrderLabel(order),
                order,
                actor,
                null
        );
    }

    public void notifyChatMessageReceived(
            ChatRoom room,
            ChatMessage message,
            ChatRoom.ParticipantSnapshot actor,
            ChatRoom.ParticipantSnapshot receiver
    ) {
        if (room == null || message == null || actor == null || receiver == null) {
            return;
        }

        String receiverUserId = receiver.getUserId();
        if (receiverUserId == null || receiverUserId.isBlank()) {
            return;
        }

        String actorUserId = actor.getUserId();
        if (actorUserId != null && actorUserId.equals(receiverUserId)) {
            return;
        }

        String messageType = message.getType() != null ? message.getType().trim().toUpperCase() : ChatMessage.MessageType.TEXT.name();
        String title = "New message from " + buildChatParticipantName(actor);
        String body = buildChatMessagePreview(messageType, message.getText());

        Notification notification = Notification.builder()
                .userId(receiverUserId)
                .type("CHAT_MESSAGE_RECEIVED")
                .title(title)
                .message(body)
                .connectionId(room.getConnectionId())
                .chatId(room.getId())
                .referenceType(message.getReferenceType())
                .referenceId(message.getReferenceId())
                .actorUserId(actor.getUserId())
                .actorUserRole(actor.getUserType())
                .actorName(actor.getDisplayName())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        saveNotification(notification);
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

        saveNotification(notification);
    }

    private void saveOrderNotification(
            String userId,
            String type,
            String title,
            String message,
            Order order,
            Order.PartySnapshot actor,
            PaymentTransaction paymentTransaction
    ) {
        if (userId == null || userId.isBlank() || order == null) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .cropId(order.getItem() != null ? order.getItem().getCropId() : null)
                .connectionId(order.getConnectionId())
                .chatId(order.getChatId())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentTransactionId(paymentTransaction != null ? paymentTransaction.getId() : null)
                .referenceType(order.getContextType() != null ? order.getContextType().name() : null)
                .referenceId(order.getContextRefId())
                .actorUserId(actor != null ? actor.getUserId() : null)
                .actorUserRole(actor != null ? actor.getUserType() : null)
                .actorName(actor != null ? actor.getDisplayName() : null)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        saveNotification(notification);
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

    private String buildPartyName(Order.PartySnapshot actor) {
        if (actor == null || actor.getDisplayName() == null || actor.getDisplayName().isBlank()) {
            return "Someone";
        }
        return actor.getDisplayName();
    }

    private String buildChatParticipantName(ChatRoom.ParticipantSnapshot actor) {
        if (actor == null || actor.getDisplayName() == null || actor.getDisplayName().isBlank()) {
            return "Someone";
        }
        return actor.getDisplayName();
    }

    private String buildChatMessagePreview(String messageType, String text) {
        String safeType = messageType == null ? ChatMessage.MessageType.TEXT.name() : messageType;
        String safeText = text == null ? null : text.trim();

        if (ChatMessage.MessageType.IMAGE.name().equalsIgnoreCase(safeType)) {
            return safeText == null || safeText.isBlank() ? "Sent an image" : "Sent an image: " + safeText;
        }

        if (safeText == null || safeText.isBlank()) {
            return "Sent you a message";
        }

        return safeText;
    }

    private String buildOrderLabel(Order order) {
        if (order == null) {
            return "your order";
        }

        String title = order.getItem() != null ? order.getItem().getTitle() : null;
        String orderCode = order.getOrderCode();

        if (title != null && !title.isBlank() && orderCode != null && !orderCode.isBlank()) {
            return title + " (" + orderCode + ")";
        }

        if (title != null && !title.isBlank()) {
            return title;
        }

        if (orderCode != null && !orderCode.isBlank()) {
            return "order " + orderCode;
        }

        return "your order";
    }

    private Order.OrderEvent getLatestOrderEvent(Order order) {
        if (order == null || order.getEvents() == null || order.getEvents().isEmpty()) {
            return null;
        }
        return order.getEvents().get(order.getEvents().size() - 1);
    }

    private void saveNotification(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        try {
            notificationPushService.sendNotification(saved);
        } catch (Exception ex) {
            log.warn("Failed to deliver push notification for notification {}", saved.getId(), ex);
        }
    }
}

