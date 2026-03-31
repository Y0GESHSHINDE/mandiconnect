package com.mandiconnect.services;

import com.mandiconnect.models.Buyer;
import com.mandiconnect.models.ChatRoom;
import com.mandiconnect.models.Connection;
import com.mandiconnect.models.CropListing;
import com.mandiconnect.models.Crops;
import com.mandiconnect.models.Farmer;
import com.mandiconnect.models.Order;
import com.mandiconnect.repositories.BuyerRepository;
import com.mandiconnect.repositories.ChatRoomRepository;
import com.mandiconnect.repositories.ConnectionRepository;
import com.mandiconnect.repositories.CropListingRepository;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_CODE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final ConnectionRepository connectionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final CropListingRepository cropListingRepository;
    private final BuyerRepository buyerRepository;
    private final FarmerRepository farmerRepository;
    private final NotificationService notificationService;
    private final ChatService chatService;

    public Order createOrder(String authenticatedEmail, CreateOrderCommand command) {
        AuthenticatedUser actor = resolveAuthenticatedBuyer(authenticatedEmail);
        String connectionId = requireValue(command.connectionId(), "connectionId");
        String chatId = requireValue(command.chatId(), "chatId");
        String cropListingId = requireValue(command.cropListingId(), "cropListingId");

        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
        validateAcceptedConnection(connection);

        Buyer buyer = resolveConnectionBuyer(connection);
        Farmer farmer = resolveConnectionFarmer(connection);

        if (!Objects.equals(actor.userId(), buyer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the connected buyer can create the order");
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
        validateChatForOrder(chatRoom, connection, actor);

        CropListing cropListing = cropListingRepository.findById(cropListingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop listing not found"));
        validateListingForOrder(cropListing, farmer, connection, cropListingId);

        double orderedQuantity = requirePositive(command.quantity(), "quantity");
        if (orderedQuantity > cropListing.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ordered quantity cannot exceed the listed quantity");
        }

        String orderedUnit = normalizeOptionalValue(command.unit());
        String listedUnit = normalizeOptionalValue(cropListing.getUnit());
        if (orderedUnit == null) {
            orderedUnit = listedUnit;
        } else if (listedUnit != null && !listedUnit.equalsIgnoreCase(orderedUnit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order unit must match the crop listing unit");
        }

        Double listingPrice = cropListing.getPrice();
        if (listingPrice == null || listingPrice <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Crop listing price must be greater than 0");
        }

        double unitPrice = listingPrice;
        double subtotalAmount = unitPrice * orderedQuantity;
        String currency = normalizeOptionalValue(command.currency());
        if (currency == null) {
            currency = "INR";
        } else {
            currency = currency.toUpperCase(Locale.ROOT);
        }

        Order.DeliveryDetails deliveryDetails = buildDeliveryDetails(command.deliveryDetails(), buyer);
        String orderCode = generateOrderCode();
        LocalDateTime now = LocalDateTime.now();

        Crops crop = cropListing.getCrop();
        Connection.ContextRef context = findConnectionContext(connection, cropListingId);
        String cropName = crop != null ? normalizeOptionalValue(crop.getName()) : context != null ? normalizeOptionalValue(context.getCropName()) : null;
        String cropVariety = crop != null ? normalizeOptionalValue(crop.getVariety()) : context != null ? normalizeOptionalValue(context.getCropVariety()) : null;
        String itemTitle = context != null && normalizeOptionalValue(context.getTitle()) != null
                ? context.getTitle()
                : buildItemTitle(cropName, cropVariety);

        Order order = Order.builder()
                .orderCode(orderCode)
                .connectionId(connection.getId())
                .chatId(chatRoom.getId())
                .contextType(Order.ContextType.CROP)
                .contextRefId(cropListingId)
                .buyerId(buyer.getId())
                .farmerId(farmer.getId())
                .buyer(buildBuyerSnapshot(buyer))
                .farmer(buildFarmerSnapshot(farmer))
                .item(Order.OrderItem.builder()
                        .cropListingId(cropListing.getId())
                        .cropId(crop != null ? normalizeOptionalValue(crop.getId()) : context != null ? normalizeOptionalValue(context.getCropId()) : null)
                        .cropName(cropName)
                        .cropVariety(cropVariety)
                        .title(itemTitle)
                        .photoUrl(normalizeOptionalValue(cropListing.getPhotoUrl()))
                        .locationCity(cropListing.getLocation() != null ? normalizeOptionalValue(cropListing.getLocation().getCity()) : null)
                        .locationState(cropListing.getLocation() != null ? normalizeOptionalValue(cropListing.getLocation().getState()) : null)
                        .listedQuantity(cropListing.getQuantity())
                        .listedUnit(listedUnit)
                        .listedPrice(unitPrice)
                        .orderedQuantity(orderedQuantity)
                        .orderedUnit(orderedUnit)
                        .agreedPrice(unitPrice)
                        .build())
                .deliveryDetails(deliveryDetails)
                .notes(normalizeOptionalValue(command.notes()))
                .subtotalAmount(subtotalAmount)
                .totalAmount(subtotalAmount)
                .currency(currency)
                .status(Order.OrderStatus.PLACED.name())
                .paymentStatus(Order.PaymentStatus.PENDING.name())
                .deliveryStatus(Order.DeliveryStatus.PENDING.name())
                .placedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .events(new ArrayList<>())
                .build();

        order.getEvents().add(Order.OrderEvent.builder()
                .type(Order.EventType.ORDER_PLACED)
                .orderStatus(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .deliveryStatus(order.getDeliveryStatus())
                .actionByUserId(actor.userId())
                .actionByUserType(actor.userType().name())
                .note("Order placed for " + itemTitle)
                .createdAt(now)
                .build());

        Order savedOrder = orderRepository.save(order);
        emitOrderPlacedSideEffects(savedOrder, actor);
        return savedOrder;
    }

    public Order getOrderById(String orderId, String authenticatedEmail) {
        Order order = orderRepository.findById(requireValue(orderId, "orderId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        authorizeOrderAccess(order, authenticatedEmail);
        return order;
    }

    public List<Order> getBuyerOrders(String buyerId, String authenticatedEmail) {
        AuthenticatedUser actor = authorizeUserAccess(authenticatedEmail, buyerId, Connection.UserType.BUYER);
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(actor.userId());
    }

    public List<Order> getFarmerOrders(String farmerId, String authenticatedEmail) {
        AuthenticatedUser actor = authorizeUserAccess(authenticatedEmail, farmerId, Connection.UserType.FARMER);
        return orderRepository.findByFarmerIdOrderByCreatedAtDesc(actor.userId());
    }

    public List<Order> getOrdersByConnection(String connectionId, String authenticatedEmail) {
        String safeConnectionId = requireValue(connectionId, "connectionId");
        List<Order> orders = orderRepository.findByConnectionIdOrderByCreatedAtDesc(safeConnectionId);
        if (orders.isEmpty()) {
            return List.of();
        }

        authorizeOrderAccess(orders.get(0), authenticatedEmail);
        return orders;
    }

    public List<Order> getOrdersByChat(String chatId, String authenticatedEmail) {
        String safeChatId = requireValue(chatId, "chatId");
        List<Order> orders = orderRepository.findByChatIdOrderByCreatedAtDesc(safeChatId);
        if (orders.isEmpty()) {
            return List.of();
        }

        authorizeOrderAccess(orders.get(0), authenticatedEmail);
        return orders;
    }

    public Order confirmOrder(String orderId, String authenticatedEmail, String note) {
        Order order = transitionOrder(
                orderId,
                authenticatedEmail,
                Connection.UserType.FARMER,
                "Only the connected farmer can confirm the order",
                Order.EventType.ORDER_CONFIRMED,
                Order.OrderStatus.CONFIRMED,
                Order.DeliveryStatus.SCHEDULED,
                normalizedTransitionNote(note, "Order confirmed by farmer"),
                Order.OrderStatus.PLACED,
                Order.OrderStatus.PAID
        );
        emitOrderConfirmedSideEffects(order);
        return order;
    }

    public Order markOrderProcessing(String orderId, String authenticatedEmail, String note) {
        Order order = transitionOrder(
                orderId,
                authenticatedEmail,
                Connection.UserType.FARMER,
                "Only the connected farmer can move the order to processing",
                Order.EventType.ORDER_PROCESSING,
                Order.OrderStatus.PROCESSING,
                Order.DeliveryStatus.PACKED,
                normalizedTransitionNote(note, "Order moved to processing"),
                Order.OrderStatus.CONFIRMED
        );
        emitOrderProcessingSideEffects(order);
        return order;
    }

    public Order dispatchOrder(String orderId, String authenticatedEmail, String note) {
        Order order = transitionOrder(
                orderId,
                authenticatedEmail,
                Connection.UserType.FARMER,
                "Only the connected farmer can dispatch the order",
                Order.EventType.ORDER_DISPATCHED,
                Order.OrderStatus.DISPATCHED,
                Order.DeliveryStatus.OUT_FOR_DELIVERY,
                normalizedTransitionNote(note, "Order dispatched"),
                Order.OrderStatus.PROCESSING
        );
        emitOrderDispatchedSideEffects(order);
        return order;
    }

    public Order deliverOrder(String orderId, String authenticatedEmail, String note) {
        Order order = transitionOrder(
                orderId,
                authenticatedEmail,
                Connection.UserType.FARMER,
                "Only the connected farmer can mark the order as delivered",
                Order.EventType.ORDER_DELIVERED,
                Order.OrderStatus.DELIVERED,
                Order.DeliveryStatus.DELIVERED,
                normalizedTransitionNote(note, "Order marked as delivered"),
                Order.OrderStatus.DISPATCHED
        );
        emitOrderDeliveredSideEffects(order);
        return order;
    }

    public Order completeOrder(String orderId, String authenticatedEmail, String note) {
        Order order = transitionOrder(
                orderId,
                authenticatedEmail,
                Connection.UserType.BUYER,
                "Only the connected buyer can complete the order",
                Order.EventType.ORDER_COMPLETED,
                Order.OrderStatus.COMPLETED,
                Order.DeliveryStatus.DELIVERED,
                normalizedTransitionNote(note, "Order completed by buyer"),
                Order.OrderStatus.DELIVERED
        );
        emitOrderCompletedSideEffects(order);
        return order;
    }

    public Order cancelOrder(String orderId, String authenticatedEmail, String note) {
        Order order = getOrderForUpdate(orderId);
        AuthenticatedUser actor = resolveOrderActor(order, authenticatedEmail);

        Order.OrderStatus currentStatus = parseOrderStatus(order.getStatus());
        if (currentStatus == Order.OrderStatus.DISPATCHED
                || currentStatus == Order.OrderStatus.DELIVERED
                || currentStatus == Order.OrderStatus.COMPLETED
                || currentStatus == Order.OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order can no longer be cancelled");
        }

        order.setStatus(Order.OrderStatus.CANCELLED.name());
        order.setDeliveryStatus(Order.DeliveryStatus.CANCELLED.name());
        if (Order.PaymentStatus.PENDING.name().equals(order.getPaymentStatus())
                || Order.PaymentStatus.INITIATED.name().equals(order.getPaymentStatus())
                || Order.PaymentStatus.FAILED.name().equals(order.getPaymentStatus())) {
            order.setPaymentStatus(Order.PaymentStatus.CANCELLED.name());
        }

        LocalDateTime now = LocalDateTime.now();
        order.setUpdatedAt(now);
        appendEvent(
                order,
                Order.EventType.ORDER_CANCELLED,
                actor,
                normalizedTransitionNote(note, "Order cancelled by " + actor.userType().name().toLowerCase(Locale.ROOT)),
                now
        );

        Order savedOrder = orderRepository.save(order);
        emitOrderCancelledSideEffects(savedOrder);
        return savedOrder;
    }

    private void validateAcceptedConnection(Connection connection) {
        String normalizedStatus = connection.getNormalizedStatus();
        if (!Connection.ConnectionStatus.ACCEPTED.name().equals(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only accepted connections can create orders");
        }
    }

    private Buyer resolveConnectionBuyer(Connection connection) {
        String buyerId = extractParticipantId(connection, Connection.UserType.BUYER);
        if (buyerId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accepted connection does not have a buyer");
        }

        return buyerRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found for connection"));
    }

    private Farmer resolveConnectionFarmer(Connection connection) {
        String farmerId = extractParticipantId(connection, Connection.UserType.FARMER);
        if (farmerId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accepted connection does not have a farmer");
        }

        return farmerRepository.findById(farmerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found for connection"));
    }

    private String extractParticipantId(Connection connection, Connection.UserType targetType) {
        if (connection.getParticipants() != null) {
            String participantId = connection.getParticipants().stream()
                    .filter(Objects::nonNull)
                    .filter(participant -> participant.getUserType() == targetType)
                    .map(Connection.ParticipantSnapshot::getUserId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (participantId != null) {
                return participantId;
            }
        }

        if (connection.getSenderRole() != null) {
            try {
                if (Connection.UserType.from(connection.getSenderRole()) == targetType) {
                    return connection.getSenderId();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (connection.getReceiverRole() != null) {
            try {
                if (Connection.UserType.from(connection.getReceiverRole()) == targetType) {
                    return connection.getReceiverId();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return null;
    }

    private void validateChatForOrder(ChatRoom chatRoom, Connection connection, AuthenticatedUser actor) {
        if (!Objects.equals(chatRoom.getConnectionId(), connection.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId does not belong to the provided connection");
        }

        if (!ChatRoom.ChatStatus.ACTIVE.name().equalsIgnoreCase(chatRoom.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active chat rooms can create orders");
        }

        if (!belongsToChat(chatRoom, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this chat room");
        }
    }

    private void validateListingForOrder(CropListing cropListing, Farmer farmer, Connection connection, String cropListingId) {
        String status = normalizeOptionalValue(cropListing.getStatus());
        if (status != null && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active crop listings can be ordered");
        }

        if (cropListing.getFarmer() == null || cropListing.getFarmer().getId() == null || cropListing.getFarmer().getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Crop listing does not have a valid farmer owner");
        }

        if (!Objects.equals(cropListing.getFarmer().getId(), farmer.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Crop listing does not belong to the connected farmer");
        }

        if (!hasCropContext(connection, cropListingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This crop listing is not part of the accepted connection context");
        }
    }

    private boolean hasCropContext(Connection connection, String cropListingId) {
        if (connection.getContexts() != null && !connection.getContexts().isEmpty()) {
            return connection.getContexts().stream()
                    .filter(Objects::nonNull)
                    .anyMatch(context -> context.getType() == Connection.ContextType.CROP
                            && Objects.equals(context.getRefId(), cropListingId));
        }

        return "CROP".equalsIgnoreCase(connection.getReferenceType())
                && Objects.equals(connection.getReferenceId(), cropListingId);
    }

    private Connection.ContextRef findConnectionContext(Connection connection, String cropListingId) {
        if (connection.getContexts() == null) {
            return null;
        }

        return connection.getContexts().stream()
                .filter(Objects::nonNull)
                .filter(context -> context.getType() == Connection.ContextType.CROP)
                .filter(context -> Objects.equals(context.getRefId(), cropListingId))
                .findFirst()
                .orElse(null);
    }

    private Order.DeliveryDetails buildDeliveryDetails(DeliveryDetailsInput input, Buyer buyer) {
        Buyer.CompanyAddress buyerAddress = buyer.getCompanyAddress();
        String contactName = firstNonBlank(input != null ? input.contactName() : null, buyer.getName());
        String contactPhone = firstNonBlank(input != null ? input.contactPhone() : null, buyer.getMobile());
        String addressLine1 = normalizeOptionalValue(input != null ? input.addressLine1() : null);
        String city = firstNonBlank(input != null ? input.city() : null, buyerAddress != null ? buyerAddress.getCity() : null);
        String state = firstNonBlank(input != null ? input.state() : null, buyerAddress != null ? buyerAddress.getState() : null);
        String country = firstNonBlank(input != null ? input.country() : null, buyerAddress != null ? buyerAddress.getCountry() : null);

        requireValue(contactName, "deliveryDetails.contactName");
        requireValue(contactPhone, "deliveryDetails.contactPhone");
        requireValue(addressLine1, "deliveryDetails.addressLine1");
        requireValue(city, "deliveryDetails.city");
        requireValue(state, "deliveryDetails.state");
        requireValue(country, "deliveryDetails.country");

        return Order.DeliveryDetails.builder()
                .contactName(contactName)
                .contactPhone(contactPhone)
                .addressLine1(addressLine1)
                .addressLine2(normalizeOptionalValue(input != null ? input.addressLine2() : null))
                .village(normalizeOptionalValue(input != null ? input.village() : null))
                .city(city)
                .state(state)
                .country(country)
                .pincode(normalizeOptionalValue(input != null ? input.pincode() : null))
                .landmark(normalizeOptionalValue(input != null ? input.landmark() : null))
                .preferredDeliveryDate(input != null ? input.preferredDeliveryDate() : null)
                .preferredTimeSlot(normalizeOptionalValue(input != null ? input.preferredTimeSlot() : null))
                .instructions(normalizeOptionalValue(input != null ? input.instructions() : null))
                .build();
    }

    private Order.PartySnapshot buildBuyerSnapshot(Buyer buyer) {
        Buyer.CompanyAddress address = buyer.getCompanyAddress();
        return Order.PartySnapshot.builder()
                .userId(buyer.getId())
                .userType(Connection.UserType.BUYER.name())
                .displayName(buyer.getName())
                .mobile(buyer.getMobile())
                .city(address != null ? address.getCity() : null)
                .state(address != null ? address.getState() : null)
                .build();
    }

    private Order.PartySnapshot buildFarmerSnapshot(Farmer farmer) {
        Farmer.FarmerAddress address = farmer.getFarmerAddress();
        return Order.PartySnapshot.builder()
                .userId(farmer.getId())
                .userType(Connection.UserType.FARMER.name())
                .displayName(farmer.getName())
                .mobile(farmer.getMobile())
                .city(address != null ? address.getCity() : null)
                .state(address != null ? address.getState() : null)
                .build();
    }

    private String buildItemTitle(String cropName, String cropVariety) {
        String safeCropName = cropName != null ? cropName : "Crop order";
        if (cropVariety == null || cropVariety.isBlank()) {
            return safeCropName;
        }
        return safeCropName + " (" + cropVariety + ")";
    }

    private void emitOrderPlacedSideEffects(Order order, AuthenticatedUser actor) {
        sendOrderSystemMessage(order, buildOrderPlacedMessage(order), actor.userId());
        notifySafely(() -> notificationService.notifyOrderPlaced(order));
    }

    private void emitOrderConfirmedSideEffects(Order order) {
        sendOrderSystemMessage(order, buildOrderActionMessage(order, "confirmed"), order.getFarmerId());
        notifySafely(() -> notificationService.notifyOrderConfirmed(order));
    }

    private void emitOrderProcessingSideEffects(Order order) {
        sendOrderSystemMessage(order, buildOrderActionMessage(order, "moved to processing"), order.getFarmerId());
        notifySafely(() -> notificationService.notifyOrderProcessing(order));
    }

    private void emitOrderDispatchedSideEffects(Order order) {
        sendOrderSystemMessage(order, buildOrderActionMessage(order, "dispatched"), order.getFarmerId());
        notifySafely(() -> notificationService.notifyOrderDispatched(order));
    }

    private void emitOrderDeliveredSideEffects(Order order) {
        sendOrderSystemMessage(order, buildOrderActionMessage(order, "marked as delivered"), order.getFarmerId());
        notifySafely(() -> notificationService.notifyOrderDelivered(order));
    }

    private void emitOrderCompletedSideEffects(Order order) {
        sendOrderSystemMessage(order, buildOrderActionMessage(order, "completed"), order.getBuyerId());
        notifySafely(() -> notificationService.notifyOrderCompleted(order));
    }

    private void emitOrderCancelledSideEffects(Order order) {
        String actorUserId = resolveLatestActorUserId(order);
        sendOrderSystemMessage(order, buildOrderActionMessage(order, "cancelled"), actorUserId);
        notifySafely(() -> notificationService.notifyOrderCancelled(order));
    }

    private void sendOrderSystemMessage(Order order, String text, String actedByUserId) {
        if (order.getChatId() == null || order.getChatId().isBlank()) {
            return;
        }

        try {
            chatService.sendSystemMessage(
                    order.getChatId(),
                    text,
                    order.getContextType() != null ? order.getContextType().name() : null,
                    order.getContextRefId(),
                    actedByUserId
            );
        } catch (Exception ignored) {
        }
    }

    private void notifySafely(Runnable action) {
        try {
            action.run();
        } catch (Exception ignored) {
        }
    }

    private String buildOrderPlacedMessage(Order order) {
        String itemLabel = order.getItem() != null && order.getItem().getTitle() != null && !order.getItem().getTitle().isBlank()
                ? order.getItem().getTitle()
                : "this crop";
        String quantityLabel = formatQuantity(order.getItem() != null ? order.getItem().getOrderedQuantity() : null,
                order.getItem() != null ? order.getItem().getOrderedUnit() : null);
        String priceLabel = formatPrice(order.getTotalAmount(), order.getCurrency());
        return "Order " + order.getOrderCode() + " placed for " + itemLabel
                + (quantityLabel != null ? " | Qty: " + quantityLabel : "")
                + (priceLabel != null ? " | Total: " + priceLabel : "");
    }

    private String buildOrderActionMessage(Order order, String action) {
        return "Order " + order.getOrderCode() + " " + action + ".";
    }

    private String resolveLatestActorUserId(Order order) {
        if (order.getEvents() == null || order.getEvents().isEmpty()) {
            return null;
        }
        Order.OrderEvent latestEvent = order.getEvents().get(order.getEvents().size() - 1);
        return latestEvent.getActionByUserId();
    }

    private String generateOrderCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "MC-ORD-"
                    + LocalDateTime.now().format(ORDER_CODE_FORMAT)
                    + "-"
                    + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (orderRepository.findByOrderCode(candidate).isEmpty()) {
                return candidate;
            }
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to generate a unique order code");
    }

    private void authorizeOrderAccess(Order order, String authenticatedEmail) {
        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(actor -> Objects.equals(actor.userId(), order.getBuyerId()) || Objects.equals(actor.userId(), order.getFarmerId()))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this order");
        }
    }

    private Order transitionOrder(
            String orderId,
            String authenticatedEmail,
            Connection.UserType requiredActorType,
            String forbiddenMessage,
            Order.EventType eventType,
            Order.OrderStatus nextStatus,
            Order.DeliveryStatus nextDeliveryStatus,
            String note,
            Order.OrderStatus... allowedCurrentStatuses
    ) {
        Order order = getOrderForUpdate(orderId);
        AuthenticatedUser actor = resolveOrderActor(order, authenticatedEmail);

        if (actor.userType() != requiredActorType) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }

        Order.OrderStatus currentStatus = parseOrderStatus(order.getStatus());
        if (Arrays.stream(allowedCurrentStatuses).noneMatch(status -> status == currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid order status transition from " + currentStatus.name() + " to " + nextStatus.name()
            );
        }

        order.setStatus(nextStatus.name());
        order.setDeliveryStatus(nextDeliveryStatus.name());
        LocalDateTime now = LocalDateTime.now();
        order.setUpdatedAt(now);
        appendEvent(order, eventType, actor, note, now);
        return orderRepository.save(order);
    }

    private void appendEvent(
            Order order,
            Order.EventType type,
            AuthenticatedUser actor,
            String note,
            LocalDateTime createdAt
    ) {
        if (order.getEvents() == null) {
            order.setEvents(new ArrayList<>());
        }

        order.getEvents().add(Order.OrderEvent.builder()
                .type(type)
                .orderStatus(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .deliveryStatus(order.getDeliveryStatus())
                .actionByUserId(actor.userId())
                .actionByUserType(actor.userType().name())
                .note(note)
                .createdAt(createdAt)
                .build());
    }

    private String normalizedTransitionNote(String note, String fallback) {
        String safeNote = normalizeOptionalValue(note);
        return safeNote != null ? safeNote : fallback;
    }

    private Order getOrderForUpdate(String orderId) {
        return orderRepository.findById(requireValue(orderId, "orderId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private AuthenticatedUser resolveOrderActor(Order order, String authenticatedEmail) {
        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(actor -> Objects.equals(actor.userId(), order.getBuyerId()) || Objects.equals(actor.userId(), order.getFarmerId()))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this order");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated user for this order");
        }

        return matches.get(0);
    }

    private Order.OrderStatus parseOrderStatus(String value) {
        String safeValue = requireValue(value, "status");
        try {
            return Order.OrderStatus.valueOf(safeValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported order status: " + value);
        }
    }

    private AuthenticatedUser authorizeUserAccess(String authenticatedEmail, String userId, Connection.UserType expectedType) {
        String safeUserId = requireValue(userId, "userId");

        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(actor -> Objects.equals(actor.userId(), safeUserId))
                .filter(actor -> actor.userType() == expectedType)
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own orders");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated user for this userId");
        }

        return matches.get(0);
    }

    private AuthenticatedUser resolveAuthenticatedBuyer(String authenticatedEmail) {
        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(actor -> actor.userType() == Connection.UserType.BUYER)
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only buyers can create orders");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated buyer");
        }

        return matches.get(0);
    }

    private List<AuthenticatedUser> resolveAuthenticatedUsers(String authenticatedEmail) {
        String normalizedEmail = requireValue(authenticatedEmail, "authenticatedEmail").toLowerCase(Locale.ROOT);
        List<AuthenticatedUser> users = new ArrayList<>();

        buyerRepository.findByEmail(normalizedEmail)
                .ifPresent(buyer -> users.add(new AuthenticatedUser(buyer.getId(), Connection.UserType.BUYER)));

        farmerRepository.findByEmail(normalizedEmail)
                .ifPresent(farmer -> users.add(new AuthenticatedUser(farmer.getId(), Connection.UserType.FARMER)));

        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found");
        }

        return users;
    }

    private boolean belongsToChat(ChatRoom room, AuthenticatedUser actor) {
        String participantKey = Connection.buildParticipantKey(actor.userId(), actor.userType().name());
        if (room.getParticipantKeys() != null && room.getParticipantKeys().contains(participantKey)) {
            return true;
        }

        return room.getParticipants() != null && room.getParticipants().stream()
                .filter(Objects::nonNull)
                .anyMatch(participant -> Objects.equals(participant.getUserId(), actor.userId()));
    }

    private double requirePositive(Double value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than 0");
        }
        return value;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private String normalizeOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        String safePrimary = normalizeOptionalValue(primary);
        if (safePrimary != null) {
            return safePrimary;
        }
        return normalizeOptionalValue(fallback);
    }

    private String formatQuantity(Double quantity, String unit) {
        if (quantity == null) {
            return null;
        }

        String quantityText = trimDecimal(quantity);
        String safeUnit = normalizeOptionalValue(unit);
        return safeUnit == null ? quantityText : quantityText + " " + safeUnit;
    }

    private String formatPrice(Double amount, String currency) {
        if (amount == null) {
            return null;
        }

        String safeCurrency = normalizeOptionalValue(currency);
        return (safeCurrency == null ? "INR" : safeCurrency.toUpperCase(Locale.ROOT)) + " " + trimDecimal(amount);
    }

    private String trimDecimal(Double value) {
        if (value == null) {
            return null;
        }

        if (Math.floor(value) == value) {
            return String.valueOf(value.longValue());
        }

        String text = value.toString();
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private record AuthenticatedUser(String userId, Connection.UserType userType) {
    }

    public record CreateOrderCommand(
            String connectionId,
            String chatId,
            String cropListingId,
            Double quantity,
            String unit,
            String currency,
            String notes,
            DeliveryDetailsInput deliveryDetails
    ) {
    }

    public record OrderActionCommand(String note) {
    }

    public record DeliveryDetailsInput(
            String contactName,
            String contactPhone,
            String addressLine1,
            String addressLine2,
            String village,
            String city,
            String state,
            String country,
            String pincode,
            String landmark,
            java.time.LocalDate preferredDeliveryDate,
            String preferredTimeSlot,
            String instructions
    ) {
    }
}
