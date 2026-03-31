package com.mandiconnect.services;

import com.mandiconnect.models.Buyer;
import com.mandiconnect.models.BuyerDemands;
import com.mandiconnect.models.ChatMessage;
import com.mandiconnect.models.ChatRoom;
import com.mandiconnect.models.Connection;
import com.mandiconnect.models.CropListing;
import com.mandiconnect.models.Crops;
import com.mandiconnect.models.Farmer;
import com.mandiconnect.repositories.BuyerDemandRepository;
import com.mandiconnect.repositories.BuyerRepository;
import com.mandiconnect.repositories.ChatMessageRepository;
import com.mandiconnect.repositories.ChatRoomRepository;
import com.mandiconnect.repositories.ConnectionRepository;
import com.mandiconnect.repositories.CropListingRepository;
import com.mandiconnect.repositories.CropRepository;
import com.mandiconnect.repositories.FarmerRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final int DEFAULT_HISTORY_SIZE = 50;
    private static final int MAX_HISTORY_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ConnectionRepository connectionRepository;
    private final BuyerRepository buyerRepository;
    private final FarmerRepository farmerRepository;
    private final BuyerDemandRepository buyerDemandRepository;
    private final CropListingRepository cropListingRepository;
    private final CropRepository cropRepository;
    private final FileUploadService fileUploadService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    public ChatRoom openChat(String connectionId, String authenticatedEmail) {
        Connection connection = findAuthorizedConnection(connectionId, authenticatedEmail);

        if (!Connection.ConnectionStatus.ACCEPTED.name().equals(connection.getNormalizedStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only accepted connections can open chat");
        }

        return ensureChatRoomForConnection(connection);
    }

    public List<ChatRoom> getChatsForUser(String userId, String authenticatedEmail) {
        AuthenticatedUser actor = authorizeUserAccess(authenticatedEmail, userId);
        ensureAcceptedChatsForUser(actor);

        return chatRoomRepository.findByParticipantKeyOrderByUpdatedAtDesc(
                Connection.buildParticipantKey(actor.userId(), actor.userType().name())
        );
    }

    public ChatRoom getChatById(String chatId, String authenticatedEmail) {
        ChatRoom room = chatRoomRepository.findById(requireValue(chatId, "chatId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));

        resolveChatActor(room, authenticatedEmail);
        return room;
    }

    public ChatHistory getChatMessages(String chatId, String authenticatedEmail, Integer page, Integer size) {
        ChatRoom room = getChatById(chatId, authenticatedEmail);

        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_HISTORY_SIZE : Math.min(size, MAX_HISTORY_SIZE);

        Page<ChatMessage> messagePage = chatMessageRepository.findByChatIdOrderByCreatedAtDesc(
                room.getId(),
                PageRequest.of(safePage, safeSize)
        );

        List<ChatMessage> messages = new ArrayList<>(messagePage.getContent());
        messages.sort(Comparator.comparing(ChatMessage::getCreatedAt));

        return new ChatHistory(
                room,
                messages,
                safePage,
                safeSize,
                messagePage.getTotalElements(),
                messagePage.getTotalPages(),
                messagePage.hasNext()
        );
    }

    public ChatDelivery sendTextMessage(
            String chatId,
            String authenticatedEmail,
            String text,
            String referenceType,
            String referenceId
    ) {
        ChatRoom room = getChatById(chatId, authenticatedEmail);
        if (!ChatRoom.ChatStatus.ACTIVE.name().equalsIgnoreCase(room.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat room is not active");
        }

        AuthenticatedUser actor = resolveChatActor(room, authenticatedEmail);
        String messageText = requireValue(text, "text");

        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .chatId(room.getId())
                .connectionId(room.getConnectionId())
                .senderId(actor.userId())
                .senderRole(actor.userType().name())
                .type(ChatMessage.MessageType.TEXT.name())
                .text(messageText)
                .referenceType(normalizeOptionalValue(referenceType))
                .referenceId(normalizeOptionalValue(referenceId))
                .createdAt(LocalDateTime.now())
                .build());

        room.setLastMessageId(message.getId());
        room.setLastMessageText(message.getText());
        room.setLastMessageType(message.getType());
        room.setLastMessageSenderId(message.getSenderId());
        room.setLastMessageAt(message.getCreatedAt());
        applyUnreadIncrement(room, actor);
        room.setUpdatedAt(message.getCreatedAt());
        ChatRoom savedRoom = chatRoomRepository.save(room);
        createChatMessageNotification(savedRoom, message, actor);

        return new ChatDelivery(savedRoom, message);
    }

    public ChatDelivery sendImageMessage(
            String chatId,
            String authenticatedEmail,
            MultipartFile file,
            String text,
            String referenceType,
            String referenceId
    ) throws IOException {
        ChatRoom room = getChatById(chatId, authenticatedEmail);
        if (!ChatRoom.ChatStatus.ACTIVE.name().equalsIgnoreCase(room.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat room is not active");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }

        AuthenticatedUser actor = resolveChatActor(room, authenticatedEmail);
        String safeText = normalizeOptionalValue(text);

        var uploadResult = fileUploadService.uploadFile(file, "ChatMessages");
        String mediaUrl = normalizeOptionalValue(uploadResult.get("secure_url"));
        String publicId = normalizeOptionalValue(uploadResult.get("public_id"));

        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .chatId(room.getId())
                .connectionId(room.getConnectionId())
                .senderId(actor.userId())
                .senderRole(actor.userType().name())
                .type(ChatMessage.MessageType.IMAGE.name())
                .text(safeText)
                .referenceType(normalizeOptionalValue(referenceType))
                .referenceId(normalizeOptionalValue(referenceId))
                .mediaUrl(mediaUrl)
                .mediaPublicId(publicId)
                .createdAt(LocalDateTime.now())
                .build());

        room.setLastMessageId(message.getId());
        room.setLastMessageText(safeText != null ? safeText : "Image");
        room.setLastMessageType(message.getType());
        room.setLastMessageSenderId(message.getSenderId());
        room.setLastMessageAt(message.getCreatedAt());
        applyUnreadIncrement(room, actor);
        room.setUpdatedAt(message.getCreatedAt());
        ChatRoom savedRoom = chatRoomRepository.save(room);
        createChatMessageNotification(savedRoom, message, actor);

        return new ChatDelivery(savedRoom, message);
    }

    public ChatReadReceipt markChatAsRead(String chatId, String authenticatedEmail) {
        ChatRoom room = getChatById(chatId, authenticatedEmail);
        AuthenticatedUser actor = resolveChatActor(room, authenticatedEmail);

        List<ChatMessage> unreadMessages = chatMessageRepository.findByChatIdAndSenderIdNotAndReadAtIsNull(
                room.getId(),
                actor.userId()
        );

        LocalDateTime now = LocalDateTime.now();
        if (!unreadMessages.isEmpty()) {
            unreadMessages.forEach(message -> message.setReadAt(now));
            chatMessageRepository.saveAll(unreadMessages);
        }

        clearUnreadCount(room, actor);
        ChatRoom savedRoom = chatRoomRepository.save(room);

        return new ChatReadReceipt(savedRoom, unreadMessages.size(), now);
    }

    public ChatDelivery sendSystemMessage(
            String chatId,
            String text,
            String referenceType,
            String referenceId,
            String actedByUserId
    ) {
        ChatRoom room = chatRoomRepository.findById(requireValue(chatId, "chatId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));

        if (!ChatRoom.ChatStatus.ACTIVE.name().equalsIgnoreCase(room.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat room is not active");
        }

        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .chatId(room.getId())
                .connectionId(room.getConnectionId())
                .senderId("SYSTEM")
                .senderRole("SYSTEM")
                .type(ChatMessage.MessageType.SYSTEM.name())
                .text(requireValue(text, "text"))
                .referenceType(normalizeOptionalValue(referenceType))
                .referenceId(normalizeOptionalValue(referenceId))
                .createdAt(now)
                .build());

        room.setLastMessageId(message.getId());
        room.setLastMessageText(message.getText());
        room.setLastMessageType(message.getType());
        room.setLastMessageSenderId(message.getSenderId());
        room.setLastMessageAt(message.getCreatedAt());
        applySystemUnreadIncrement(room, actedByUserId);
        room.setUpdatedAt(message.getCreatedAt());
        ChatRoom savedRoom = chatRoomRepository.save(room);

        ChatDelivery delivery = new ChatDelivery(savedRoom, message);
        messagingTemplate.convertAndSend("/topic/chat/" + savedRoom.getId(), delivery);
        return delivery;
    }

    public ChatRoom ensureChatRoomForConnection(Connection connection) {
        Connection safeConnection = prepareConnectionForChat(connection);
        if (safeConnection == null || safeConnection.getId() == null || safeConnection.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot create chat without a valid connection");
        }

        if (!Connection.ConnectionStatus.ACCEPTED.name().equals(safeConnection.getNormalizedStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat can only be created for accepted connections");
        }

        ChatRoom existingRoom = chatRoomRepository.findByConnectionId(safeConnection.getId()).orElse(null);
        if (existingRoom != null) {
            ChatRoom synchronizedRoom = syncChatRoom(existingRoom, safeConnection);
            if (!Objects.equals(safeConnection.getChatId(), synchronizedRoom.getId())) {
                safeConnection.setChatId(synchronizedRoom.getId());
                connectionRepository.save(safeConnection);
            }
            return synchronizedRoom;
        }

        LocalDateTime now = LocalDateTime.now();
        ChatRoom room = ChatRoom.builder()
                .connectionId(safeConnection.getId())
                .participantKeys(new ArrayList<>(safeConnection.getParticipantKeys()))
                .participants(mapParticipants(safeConnection))
                .status(ChatRoom.ChatStatus.ACTIVE.name())
                .createdAt(now)
                .updatedAt(now)
                .build();

        room = chatRoomRepository.save(room);

        ChatMessage systemMessage = createSystemMessage(room, safeConnection);
        room.setLastMessageId(systemMessage.getId());
        room.setLastMessageType(systemMessage.getType());
        room.setLastMessageText(systemMessage.getText());
        room.setLastMessageSenderId(systemMessage.getSenderId());
        room.setLastMessageAt(systemMessage.getCreatedAt());
        room.setUnreadCountBuyer(0);
        room.setUnreadCountFarmer(0);
        room.setUpdatedAt(systemMessage.getCreatedAt());
        room = chatRoomRepository.save(room);

        if (!Objects.equals(safeConnection.getChatId(), room.getId())) {
            safeConnection.setChatId(room.getId());
            connectionRepository.save(safeConnection);
        }

        return room;
    }

    private void ensureAcceptedChatsForUser(AuthenticatedUser actor) {
        List<Connection> candidateConnections = new ArrayList<>();
        candidateConnections.addAll(connectionRepository.findBySenderIdOrderByCreatedAtDesc(actor.userId()));
        candidateConnections.addAll(connectionRepository.findByReceiverIdOrderByCreatedAtDesc(actor.userId()));

        Set<String> seenConnectionIds = new LinkedHashSet<>();
        for (Connection connection : candidateConnections) {
            if (connection == null || connection.getId() == null || !seenConnectionIds.add(connection.getId())) {
                continue;
            }

            Connection safeConnection = prepareConnectionForChat(connection);
            if (!Connection.ConnectionStatus.ACCEPTED.name().equals(safeConnection.getNormalizedStatus())) {
                continue;
            }

            if (!belongsToConnection(safeConnection, actor)) {
                continue;
            }

            ensureChatRoomForConnection(safeConnection);
        }
    }

    private ChatRoom syncChatRoom(ChatRoom room, Connection connection) {
        boolean changed = false;

        if (!Objects.equals(room.getConnectionId(), connection.getId())) {
            room.setConnectionId(connection.getId());
            changed = true;
        }

        List<String> participantKeys = new ArrayList<>(connection.getParticipantKeys());
        if (!Objects.equals(room.getParticipantKeys(), participantKeys)) {
            room.setParticipantKeys(participantKeys);
            changed = true;
        }

        List<ChatRoom.ParticipantSnapshot> participants = mapParticipants(connection);
        if (!Objects.equals(room.getParticipants(), participants)) {
            room.setParticipants(participants);
            changed = true;
        }

        String nextStatus = Connection.ConnectionStatus.CLOSED.name().equals(connection.getNormalizedStatus())
                ? ChatRoom.ChatStatus.CLOSED.name()
                : ChatRoom.ChatStatus.ACTIVE.name();
        if (!Objects.equals(room.getStatus(), nextStatus)) {
            room.setStatus(nextStatus);
            changed = true;
        }

        if (room.getUnreadCountBuyer() == null) {
            room.setUnreadCountBuyer(0);
            changed = true;
        }

        if (room.getUnreadCountFarmer() == null) {
            room.setUnreadCountFarmer(0);
            changed = true;
        }

        if (changed) {
            room.setUpdatedAt(LocalDateTime.now());
            return chatRoomRepository.save(room);
        }

        return room;
    }

    private ChatMessage createSystemMessage(ChatRoom room, Connection connection) {
        Connection.ContextRef primaryContext = connection.getContexts() == null || connection.getContexts().isEmpty()
                ? null
                : connection.getContexts().get(0);

        String label = primaryContext != null && primaryContext.getTitle() != null && !primaryContext.getTitle().isBlank()
                ? primaryContext.getTitle()
                : "your connection";

        return chatMessageRepository.save(ChatMessage.builder()
                .chatId(room.getId())
                .connectionId(connection.getId())
                .senderId("SYSTEM")
                .senderRole("SYSTEM")
                .type(ChatMessage.MessageType.SYSTEM.name())
                .text("Connection accepted for " + label + ". You can start chatting now.")
                .referenceType(primaryContext != null && primaryContext.getType() != null ? primaryContext.getType().name() : null)
                .referenceId(primaryContext != null ? primaryContext.getRefId() : null)
                .readAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Connection findAuthorizedConnection(String connectionId, String authenticatedEmail) {
        Connection connection = connectionRepository.findById(requireValue(connectionId, "connectionId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));

        Connection safeConnection = prepareConnectionForChat(connection);
        resolveConnectionActor(safeConnection, authenticatedEmail);
        return safeConnection;
    }

    private Connection prepareConnectionForChat(Connection connection) {
        if (connection == null) {
            return null;
        }

        boolean changed = false;
        String originalStatus = connection.getStatus();
        connection.normalizeStatus();
        changed = changed || !Objects.equals(originalStatus, connection.getStatus());

        if ((connection.getParticipantKeys() == null || connection.getParticipantKeys().isEmpty())
                && connection.getSenderId() != null && connection.getReceiverId() != null
                && connection.getSenderRole() != null && connection.getReceiverRole() != null) {
            connection.setParticipantKeys(buildParticipantKeys(
                    connection.getSenderId(),
                    connection.getSenderRole(),
                    connection.getReceiverId(),
                    connection.getReceiverRole()
            ));
            changed = true;
        }

        if (connection.getParticipants() == null || connection.getParticipants().isEmpty()) {
            List<ChatRoom.ParticipantSnapshot> mappedParticipants = buildParticipantsFromConnection(connection);
            if (!mappedParticipants.isEmpty()) {
                connection.setParticipants(mappedParticipants.stream()
                        .map(participant -> Connection.ParticipantSnapshot.builder()
                                .userId(participant.getUserId())
                                .userType(Connection.UserType.from(participant.getUserType()))
                                .displayName(participant.getDisplayName())
                                .city(participant.getCity())
                                .state(participant.getState())
                                .build())
                        .toList());
                changed = true;
            }
        }

        if (connection.getContexts() != null) {
            for (Connection.ContextRef context : connection.getContexts()) {
                changed = enrichContextSnapshotIfPossible(context) || changed;
            }
        }

        if (changed) {
            connection.syncLegacyFields();
            return connectionRepository.save(connection);
        }

        return connection;
    }

    private List<ChatRoom.ParticipantSnapshot> buildParticipantsFromConnection(Connection connection) {
        List<ChatRoom.ParticipantSnapshot> participants = new ArrayList<>();

        if (connection.getSenderId() != null && connection.getSenderRole() != null) {
            ChatRoom.ParticipantSnapshot sender = loadParticipant(connection.getSenderId(), connection.getSenderRole());
            if (sender != null) {
                participants.add(sender);
            }
        }

        if (connection.getReceiverId() != null && connection.getReceiverRole() != null) {
            ChatRoom.ParticipantSnapshot receiver = loadParticipant(connection.getReceiverId(), connection.getReceiverRole());
            if (receiver != null && participants.stream().noneMatch(existing -> Objects.equals(existing.getUserId(), receiver.getUserId()))) {
                participants.add(receiver);
            }
        }

        participants.sort(Comparator.comparing(participant -> participant.getUserType() + ":" + participant.getUserId()));
        return participants;
    }

    private boolean enrichContextSnapshotIfPossible(Connection.ContextRef context) {
        if (context == null || context.getType() == null || context.getRefId() == null || context.getRefId().isBlank()) {
            return false;
        }

        if (context.getType() == Connection.ContextType.CROP) {
            return cropListingRepository.findById(context.getRefId())
                    .map(cropListing -> hydrateCropContext(context, cropListing))
                    .orElse(false);
        }

        return buyerDemandRepository.findById(context.getRefId())
                .map(demand -> hydrateDemandContext(context, demand))
                .orElse(false);
    }

    private boolean hydrateCropContext(Connection.ContextRef context, CropListing cropListing) {
        Crops crop = cropListing.getCrop();
        CropListing.Location location = cropListing.getLocation();

        String cropId = crop != null ? normalizeOptionalValue(crop.getId()) : normalizeOptionalValue(context.getCropId());
        String cropName = crop != null ? normalizeOptionalValue(crop.getName()) : normalizeOptionalValue(context.getCropName());
        String cropVariety = crop != null ? normalizeOptionalValue(crop.getVariety()) : normalizeOptionalValue(context.getCropVariety());
        Double quantity = cropListing.getQuantity();
        String unit = normalizeOptionalValue(cropListing.getUnit());
        Double price = cropListing.getPrice();
        String currency = "INR";
        String locationCity = location != null ? normalizeOptionalValue(location.getCity()) : null;
        String locationState = location != null ? normalizeOptionalValue(location.getState()) : null;
        String photoUrl = normalizeOptionalValue(cropListing.getPhotoUrl());
        String title = buildContextTitle(Connection.ContextType.CROP, cropName, cropVariety);
        String subtitle = buildContextSubtitle(Connection.ContextType.CROP, quantity, unit, price, currency, locationCity, locationState);

        return applyContextSnapshot(context, cropId, cropName, cropVariety, title, subtitle, quantity, unit, price, currency, locationCity, locationState, photoUrl);
    }

    private boolean hydrateDemandContext(Connection.ContextRef context, BuyerDemands demand) {
        String cropId = normalizeOptionalValue(demand.getCropId());
        Crops crop = cropId != null ? cropRepository.findById(cropId).orElse(null) : null;
        Double quantity = demand.getRequiredQuantity() != null ? demand.getRequiredQuantity().getValue() : null;
        String unit = demand.getRequiredQuantity() != null ? normalizeOptionalValue(demand.getRequiredQuantity().getUnit()) : null;
        Double price = demand.getExpectedPrice() != null ? demand.getExpectedPrice().getValue() : null;
        String currency = demand.getExpectedPrice() != null ? normalizeOptionalValue(demand.getExpectedPrice().getCurrency()) : "INR";
        String cropName = crop != null ? normalizeOptionalValue(crop.getName()) : normalizeOptionalValue(context.getCropName());
        String cropVariety = crop != null ? normalizeOptionalValue(crop.getVariety()) : normalizeOptionalValue(context.getCropVariety());
        String title = buildContextTitle(Connection.ContextType.DEMAND, cropName, cropVariety);
        String subtitle = buildContextSubtitle(Connection.ContextType.DEMAND, quantity, unit, price, currency, null, null);

        return applyContextSnapshot(context, cropId, cropName, cropVariety, title, subtitle, quantity, unit, price, currency, null, null, null);
    }

    private boolean applyContextSnapshot(
            Connection.ContextRef context,
            String cropId,
            String cropName,
            String cropVariety,
            String title,
            String subtitle,
            Double quantity,
            String unit,
            Double price,
            String currency,
            String locationCity,
            String locationState,
            String photoUrl
    ) {
        boolean changed = false;
        changed = setIfDifferent(context.getCropId(), cropId, context::setCropId) || changed;
        changed = setIfDifferent(context.getCropName(), cropName, context::setCropName) || changed;
        changed = setIfDifferent(context.getCropVariety(), cropVariety, context::setCropVariety) || changed;
        changed = setIfDifferent(context.getTitle(), title, context::setTitle) || changed;
        changed = setIfDifferent(context.getSubtitle(), subtitle, context::setSubtitle) || changed;
        changed = setIfDifferent(context.getQuantity(), quantity, context::setQuantity) || changed;
        changed = setIfDifferent(context.getUnit(), unit, context::setUnit) || changed;
        changed = setIfDifferent(context.getPrice(), price, context::setPrice) || changed;
        changed = setIfDifferent(context.getCurrency(), currency, context::setCurrency) || changed;
        changed = setIfDifferent(context.getLocationCity(), locationCity, context::setLocationCity) || changed;
        changed = setIfDifferent(context.getLocationState(), locationState, context::setLocationState) || changed;
        changed = setIfDifferent(context.getPhotoUrl(), photoUrl, context::setPhotoUrl) || changed;
        return changed;
    }

    private String buildContextTitle(Connection.ContextType type, String cropName, String cropVariety) {
        String baseCropName = cropName != null ? cropName : (type == Connection.ContextType.DEMAND ? "Buyer demand" : "Crop listing");
        if (cropVariety == null || cropVariety.isBlank()) {
            return baseCropName;
        }
        return baseCropName + " (" + cropVariety + ")";
    }

    private String buildContextSubtitle(
            Connection.ContextType type,
            Double quantity,
            String unit,
            Double price,
            String currency,
            String locationCity,
            String locationState
    ) {
        List<String> parts = new ArrayList<>();

        String quantityLabel = formatQuantity(quantity, unit);
        if (quantityLabel != null) {
            parts.add(type == Connection.ContextType.DEMAND ? "Need " + quantityLabel : quantityLabel);
        }

        String priceLabel = formatPrice(price, currency);
        if (priceLabel != null) {
            parts.add(type == Connection.ContextType.DEMAND ? "Expected " + priceLabel : priceLabel);
        }

        String locationLabel = joinNonBlank(locationCity, locationState);
        if (locationLabel != null && type == Connection.ContextType.CROP) {
            parts.add(locationLabel);
        }

        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String formatQuantity(Double quantity, String unit) {
        if (quantity == null) {
            return null;
        }

        String quantityText = trimDecimal(quantity);
        String safeUnit = normalizeOptionalValue(unit);
        return safeUnit == null ? quantityText : quantityText + " " + safeUnit;
    }

    private String formatPrice(Double price, String currency) {
        if (price == null) {
            return null;
        }

        String safeCurrency = normalizeOptionalValue(currency);
        String prefix = safeCurrency == null ? "INR" : safeCurrency.toUpperCase(Locale.ROOT);
        return prefix + " " + trimDecimal(price);
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

    private String joinNonBlank(String first, String second) {
        String safeFirst = normalizeOptionalValue(first);
        String safeSecond = normalizeOptionalValue(second);

        if (safeFirst == null) {
            return safeSecond;
        }
        if (safeSecond == null) {
            return safeFirst;
        }
        return safeFirst + ", " + safeSecond;
    }

    private List<ChatRoom.ParticipantSnapshot> mapParticipants(Connection connection) {
        List<ChatRoom.ParticipantSnapshot> participants = new ArrayList<>();
        if (connection.getParticipants() == null) {
            return participants;
        }

        for (Connection.ParticipantSnapshot participant : connection.getParticipants()) {
            if (participant == null) {
                continue;
            }

            participants.add(ChatRoom.ParticipantSnapshot.builder()
                    .userId(participant.getUserId())
                    .userType(participant.getUserType() != null ? participant.getUserType().name() : null)
                    .displayName(participant.getDisplayName())
                    .city(participant.getCity())
                    .state(participant.getState())
                    .build());
        }

        participants.sort(Comparator.comparing(participant -> participant.getUserType() + ":" + participant.getUserId()));
        return participants;
    }

    private List<String> buildParticipantKeys(String firstUserId, String firstUserRole, String secondUserId, String secondUserRole) {
        List<String> participantKeys = new ArrayList<>();
        participantKeys.add(Connection.buildParticipantKey(firstUserId, firstUserRole));
        participantKeys.add(Connection.buildParticipantKey(secondUserId, secondUserRole));
        participantKeys.sort(String::compareTo);
        return participantKeys;
    }

    private ChatRoom.ParticipantSnapshot loadParticipant(String userId, String userRole) {
        Connection.UserType role = parseUserType(userRole);

        if (role == Connection.UserType.BUYER) {
            Buyer buyer = buyerRepository.findById(userId).orElse(null);
            if (buyer == null) {
                return null;
            }

            return ChatRoom.ParticipantSnapshot.builder()
                    .userId(buyer.getId())
                    .userType(role.name())
                    .displayName(buyer.getName())
                    .city(buyer.getCompanyAddress() != null ? buyer.getCompanyAddress().getCity() : null)
                    .state(buyer.getCompanyAddress() != null ? buyer.getCompanyAddress().getState() : null)
                    .build();
        }

        Farmer farmer = farmerRepository.findById(userId).orElse(null);
        if (farmer == null) {
            return null;
        }

        return ChatRoom.ParticipantSnapshot.builder()
                .userId(farmer.getId())
                .userType(role.name())
                .displayName(farmer.getName())
                .city(farmer.getFarmerAddress() != null ? farmer.getFarmerAddress().getCity() : null)
                .state(farmer.getFarmerAddress() != null ? farmer.getFarmerAddress().getState() : null)
                .build();
    }

    private AuthenticatedUser authorizeUserAccess(String authenticatedEmail, String userId) {
        String safeUserId = requireValue(userId, "userId");

        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(candidate -> candidate.userId().equals(safeUserId))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own chats");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated user for this userId");
        }

        return matches.get(0);
    }

    private AuthenticatedUser resolveChatActor(ChatRoom room, String authenticatedEmail) {
        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(candidate -> belongsToChat(room, candidate))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this chat");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated user for this chat");
        }

        return matches.get(0);
    }

    private AuthenticatedUser resolveConnectionActor(Connection connection, String authenticatedEmail) {
        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(candidate -> belongsToConnection(connection, candidate))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this connection");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated user for this connection");
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
                .anyMatch(participant -> Objects.equals(participant.getUserId(), actor.userId()));
    }

    private boolean belongsToConnection(Connection connection, AuthenticatedUser actor) {
        String participantKey = Connection.buildParticipantKey(actor.userId(), actor.userType().name());
        if (connection.getParticipantKeys() != null && connection.getParticipantKeys().contains(participantKey)) {
            return true;
        }

        return Objects.equals(connection.getSenderId(), actor.userId())
                || Objects.equals(connection.getReceiverId(), actor.userId());
    }

    private Connection.UserType parseUserType(String value) {
        try {
            return Connection.UserType.from(requireValue(value, "userRole"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userRole: " + value);
        }
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private void createChatMessageNotification(ChatRoom room, ChatMessage message, AuthenticatedUser actor) {
        if (room == null || message == null || actor == null) {
            return;
        }

        ChatRoom.ParticipantSnapshot actorParticipant = findChatParticipant(room, actor.userId());
        ChatRoom.ParticipantSnapshot receiver = findOtherChatParticipant(room, actor.userId());

        if (actorParticipant == null || receiver == null) {
            return;
        }

        try {
            notificationService.notifyChatMessageReceived(room, message, actorParticipant, receiver);
        } catch (Exception ex) {
            log.warn("Failed to create chat notification for chat {} and message {}", room.getId(), message.getId(), ex);
        }
    }

    private ChatRoom.ParticipantSnapshot findChatParticipant(ChatRoom room, String userId) {
        if (room == null || room.getParticipants() == null || userId == null || userId.isBlank()) {
            return null;
        }

        return room.getParticipants().stream()
                .filter(participant -> participant != null && Objects.equals(participant.getUserId(), userId))
                .findFirst()
                .orElse(null);
    }

    private ChatRoom.ParticipantSnapshot findOtherChatParticipant(ChatRoom room, String actorUserId) {
        if (room == null || room.getParticipants() == null || actorUserId == null || actorUserId.isBlank()) {
            return null;
        }

        return room.getParticipants().stream()
                .filter(participant -> participant != null && !Objects.equals(participant.getUserId(), actorUserId))
                .findFirst()
                .orElse(null);
    }

    private String normalizeOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private <T> boolean setIfDifferent(T currentValue, T newValue, java.util.function.Consumer<T> setter) {
        if (Objects.equals(currentValue, newValue)) {
            return false;
        }
        setter.accept(newValue);
        return true;
    }

    private void applyUnreadIncrement(ChatRoom room, AuthenticatedUser actor) {
        if (actor.userType() == Connection.UserType.BUYER) {
            room.setUnreadCountFarmer(safeUnread(room.getUnreadCountFarmer()) + 1);
            return;
        }

        room.setUnreadCountBuyer(safeUnread(room.getUnreadCountBuyer()) + 1);
    }

    private void clearUnreadCount(ChatRoom room, AuthenticatedUser actor) {
        if (actor.userType() == Connection.UserType.BUYER) {
            room.setUnreadCountBuyer(0);
            return;
        }

        room.setUnreadCountFarmer(0);
    }

    private int safeUnread(Integer value) {
        return value == null ? 0 : value;
    }

    private void applySystemUnreadIncrement(ChatRoom room, String actedByUserId) {
        String buyerId = getParticipantId(room, Connection.UserType.BUYER.name());
        String farmerId = getParticipantId(room, Connection.UserType.FARMER.name());

        if (actedByUserId != null && actedByUserId.equals(buyerId)) {
            room.setUnreadCountFarmer(safeUnread(room.getUnreadCountFarmer()) + 1);
            return;
        }

        if (actedByUserId != null && actedByUserId.equals(farmerId)) {
            room.setUnreadCountBuyer(safeUnread(room.getUnreadCountBuyer()) + 1);
            return;
        }

        room.setUnreadCountBuyer(safeUnread(room.getUnreadCountBuyer()) + 1);
        room.setUnreadCountFarmer(safeUnread(room.getUnreadCountFarmer()) + 1);
    }

    private String getParticipantId(ChatRoom room, String userType) {
        if (room.getParticipants() == null) {
            return null;
        }

        return room.getParticipants().stream()
                .filter(Objects::nonNull)
                .filter(participant -> userType.equalsIgnoreCase(participant.getUserType()))
                .map(ChatRoom.ParticipantSnapshot::getUserId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private record AuthenticatedUser(String userId, Connection.UserType userType) {
    }

    public record ChatHistory(
            ChatRoom chat,
            List<ChatMessage> messages,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record ChatDelivery(ChatRoom chat, ChatMessage message) {
    }

    public record ChatReadReceipt(ChatRoom chat, int markedCount, LocalDateTime readAt) {
    }
}
