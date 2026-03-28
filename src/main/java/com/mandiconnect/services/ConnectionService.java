package com.mandiconnect.services;

import com.mandiconnect.models.Buyer;
import com.mandiconnect.models.BuyerDemands;
import com.mandiconnect.models.Connection;
import com.mandiconnect.models.CropListing;
import com.mandiconnect.models.Farmer;
import com.mandiconnect.repositories.BuyerDemandRepository;
import com.mandiconnect.repositories.BuyerRepository;
import com.mandiconnect.repositories.ConnectionRepository;
import com.mandiconnect.repositories.CropListingRepository;
import com.mandiconnect.repositories.FarmerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final BuyerRepository buyerRepository;
    private final FarmerRepository farmerRepository;
    private final BuyerDemandRepository buyerDemandRepository;
    private final CropListingRepository cropListingRepository;

    public Connection sendRequest(
            String authenticatedEmail,
            String senderId,
            String senderRole,
            String receiverId,
            String receiverRole,
            String referenceType,
            String referenceId
    ) {
        AuthenticatedUser sender = resolveAuthenticatedSender(authenticatedEmail, senderId, senderRole);
        Connection.ContextRef context = requireContext(referenceType, referenceId);
        ResolvedContext resolvedContext = resolveContext(context);

        validateDirection(sender.userType(), resolvedContext.receiver().userType(), context.getType());
        validateRequestedReceiver(receiverId, receiverRole, resolvedContext.receiver());

        if (sender.userId().equals(resolvedContext.receiver().userId())
                && sender.userType() == resolvedContext.receiver().userType()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender and receiver cannot be the same user");
        }

        LocalDateTime now = LocalDateTime.now();
        Connection existingConnection = findExistingConnection(
                sender.userId(),
                sender.userType().name(),
                resolvedContext.receiver().userId(),
                resolvedContext.receiver().userType().name()
        ).map(this::prepareConnection).orElse(null);

        if (existingConnection != null) {
            return handleExistingConnection(existingConnection, sender, resolvedContext, now);
        }

        Connection newConnection = Connection.builder()
                .pairKey(Connection.buildPairKey(
                        sender.userId(),
                        sender.userType().name(),
                        resolvedContext.receiver().userId(),
                        resolvedContext.receiver().userType().name()
                ))
                .participantKeys(buildParticipantKeys(sender, resolvedContext.receiver()))
                .participants(buildParticipants(sender, resolvedContext.receiver()))
                .requestedByUserId(sender.userId())
                .requestedByUserType(sender.userType())
                .status(Connection.ConnectionStatus.PENDING.name())
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        newConnection.addContext(resolvedContext.context());
        newConnection.syncLegacyFields();
        return connectionRepository.save(newConnection);
    }

    public Connection acceptRequest(String connectionId, String authenticatedEmail) {
        Connection connection = connectionRepository.findById(requireValue(connectionId, "connectionId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));

        connection = prepareConnection(connection);
        AuthenticatedUser actor = resolveConnectionActor(connection, authenticatedEmail);

        if (!Connection.ConnectionStatus.PENDING.name().equals(connection.getNormalizedStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending connections can be accepted");
        }

        if (isRequester(connection, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requester cannot accept the same connection request");
        }

        LocalDateTime now = LocalDateTime.now();
        connection.setStatus(Connection.ConnectionStatus.ACCEPTED.name());
        connection.setActionByUserId(actor.userId());
        connection.setRespondedAt(now);
        connection.setConnectedAt(now);
        connection.setUpdatedAt(now);
        connection.syncLegacyFields();

        return connectionRepository.save(connection);
    }

    public Connection rejectRequest(String connectionId, String authenticatedEmail) {
        Connection connection = connectionRepository.findById(requireValue(connectionId, "connectionId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));

        connection = prepareConnection(connection);
        AuthenticatedUser actor = resolveConnectionActor(connection, authenticatedEmail);

        if (!Connection.ConnectionStatus.PENDING.name().equals(connection.getNormalizedStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending connections can be rejected");
        }

        if (isRequester(connection, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requester cannot reject the same connection request");
        }

        LocalDateTime now = LocalDateTime.now();
        connection.setStatus(Connection.ConnectionStatus.REJECTED.name());
        connection.setActionByUserId(actor.userId());
        connection.setRespondedAt(now);
        connection.setConnectedAt(null);
        connection.setUpdatedAt(now);
        connection.syncLegacyFields();

        return connectionRepository.save(connection);
    }

    public List<Connection> getIncomingRequests(String userId, String authenticatedEmail) {
        AuthenticatedUser actor = authorizeUserAccess(authenticatedEmail, userId);

        List<Connection> connections = new ArrayList<>();
        connections.addAll(connectionRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(
                actor.userId(),
                Connection.ConnectionStatus.PENDING.name()
        ));
        connections.addAll(connectionRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(
                actor.userId(),
                "REQUESTED"
        ));

        return deduplicateAndPrepare(connections);
    }

    public List<Connection> getSentRequests(String userId, String authenticatedEmail) {
        AuthenticatedUser actor = authorizeUserAccess(authenticatedEmail, userId);
        return deduplicateAndPrepare(
                connectionRepository.findBySenderIdOrderByCreatedAtDesc(actor.userId())
        );
    }

    public List<Connection> getAllConnections(String userId, String authenticatedEmail) {
        AuthenticatedUser actor = authorizeUserAccess(authenticatedEmail, userId);

        List<Connection> connections = new ArrayList<>();
        connections.addAll(connectionRepository.findBySenderIdOrderByCreatedAtDesc(actor.userId()));
        connections.addAll(connectionRepository.findByReceiverIdOrderByCreatedAtDesc(actor.userId()));

        List<Connection> prepared = deduplicateAndPrepare(connections);
        prepared.sort(Comparator
                .comparing(Connection::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Connection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return prepared;
    }

    public Optional<Connection> getConnectionStatus(
            String authenticatedEmail,
            String senderId,
            String senderRole,
            String otherUserId,
            String otherUserRole
    ) {
        AuthenticatedUser actor = resolveAuthenticatedSender(authenticatedEmail, senderId, senderRole);
        String safeOtherUserId = requireValue(otherUserId, "otherUserId");
        Connection.UserType otherType = parseUserType(otherUserRole, "otherUserRole");

        return findExistingConnection(actor.userId(), actor.userType().name(), safeOtherUserId, otherType.name())
                .map(this::prepareConnection);
    }

    public Connection getConnectionById(String connectionId, String authenticatedEmail) {
        Connection connection = connectionRepository.findById(requireValue(connectionId, "connectionId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));

        connection = prepareConnection(connection);
        resolveConnectionActor(connection, authenticatedEmail);
        return connection;
    }

    private Connection handleExistingConnection(
            Connection existingConnection,
            AuthenticatedUser sender,
            ResolvedContext resolvedContext,
            LocalDateTime now
    ) {
        String normalizedStatus = existingConnection.getNormalizedStatus();

        if (Connection.ConnectionStatus.PENDING.name().equals(normalizedStatus)) {
            if (isRequester(existingConnection, sender)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Connection request already pending");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Incoming connection request already pending");
        }

        refreshParticipants(existingConnection, sender, resolvedContext.receiver());

        if (Connection.ConnectionStatus.ACCEPTED.name().equals(normalizedStatus)) {
            int initialContextCount = existingConnection.getContexts().size();
            existingConnection.addContext(resolvedContext.context());

            if (existingConnection.getContexts().size() == initialContextCount) {
                return existingConnection;
            }

            existingConnection.setUpdatedAt(now);
            existingConnection.syncLegacyFields();
            return connectionRepository.save(existingConnection);
        }

        existingConnection.setStatus(Connection.ConnectionStatus.PENDING.name());
        existingConnection.setRequestedByUserId(sender.userId());
        existingConnection.setRequestedByUserType(sender.userType());
        existingConnection.setActionByUserId(null);
        existingConnection.setRequestedAt(now);
        existingConnection.setRespondedAt(null);
        existingConnection.setConnectedAt(null);
        existingConnection.setUpdatedAt(now);
        existingConnection.setContexts(new ArrayList<>());
        existingConnection.addContext(resolvedContext.context());
        existingConnection.syncLegacyFields();

        return connectionRepository.save(existingConnection);
    }

    private AuthenticatedUser resolveAuthenticatedSender(String authenticatedEmail, String senderId, String senderRole) {
        List<AuthenticatedUser> candidates = resolveAuthenticatedUsers(authenticatedEmail);
        String normalizedSenderId = normalizeOptionalValue(senderId);
        Connection.UserType requestedRole = senderRole == null || senderRole.isBlank()
                ? null
                : parseUserType(senderRole, "senderRole");

        List<AuthenticatedUser> matches = candidates.stream()
                .filter(candidate -> normalizedSenderId == null || candidate.userId().equals(normalizedSenderId))
                .filter(candidate -> requestedRole == null || candidate.userType() == requestedRole)
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user does not match senderId/senderRole");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated user. Provide senderId and senderRole");
        }

        return matches.get(0);
    }

    private AuthenticatedUser authorizeUserAccess(String authenticatedEmail, String userId) {
        String safeUserId = requireValue(userId, "userId");

        List<AuthenticatedUser> matches = resolveAuthenticatedUsers(authenticatedEmail).stream()
                .filter(candidate -> candidate.userId().equals(safeUserId))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own connection records");
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous authenticated user for this userId");
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

    private boolean belongsToConnection(Connection connection, AuthenticatedUser candidate) {
        String participantKey = Connection.buildParticipantKey(candidate.userId(), candidate.userType().name());
        if (connection.getParticipantKeys() != null && connection.getParticipantKeys().contains(participantKey)) {
            return true;
        }

        return candidate.userId().equals(connection.getSenderId())
                || candidate.userId().equals(connection.getReceiverId());
    }

    private boolean isRequester(Connection connection, AuthenticatedUser actor) {
        return Objects.equals(connection.getRequestedByUserId(), actor.userId())
                && connection.getRequestedByUserType() == actor.userType();
    }

    private ResolvedContext resolveContext(Connection.ContextRef context) {
        if (context.getType() == Connection.ContextType.CROP) {
            CropListing cropListing = cropListingRepository.findById(context.getRefId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop listing not found"));

            validateListingAvailability(cropListing);

            if (cropListing.getFarmer() == null || cropListing.getFarmer().getId() == null || cropListing.getFarmer().getId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Crop listing does not have a valid farmer owner");
            }

            Farmer farmer = farmerRepository.findById(cropListing.getFarmer().getId())
                    .orElse(cropListing.getFarmer());

            context.setCropId(cropListing.getCrop() != null ? cropListing.getCrop().getId() : null);
            return new ResolvedContext(context, mapFarmer(farmer));
        }

        BuyerDemands demand = buyerDemandRepository.findById(context.getRefId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer demand not found"));

        validateDemandAvailability(demand);

        Buyer buyer = buyerRepository.findById(requireValue(demand.getBuyerId(), "buyerId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found for demand"));

        context.setCropId(normalizeOptionalValue(demand.getCropId()));
        return new ResolvedContext(context, mapBuyer(buyer));
    }

    private void validateDirection(
            Connection.UserType senderType,
            Connection.UserType receiverType,
            Connection.ContextType contextType
    ) {
        if (contextType == Connection.ContextType.CROP) {
            if (senderType != Connection.UserType.BUYER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only buyers can send connection requests for CROP context");
            }
            if (receiverType != Connection.UserType.FARMER) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CROP context must point to a farmer");
            }
            return;
        }

        if (senderType != Connection.UserType.FARMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only farmers can send connection requests for DEMAND context");
        }
        if (receiverType != Connection.UserType.BUYER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DEMAND context must point to a buyer");
        }
    }

    private void validateRequestedReceiver(String receiverId, String receiverRole, AuthenticatedUser actualReceiver) {
        String requestedReceiverId = normalizeOptionalValue(receiverId);
        if (requestedReceiverId != null && !requestedReceiverId.equals(actualReceiver.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverId does not match the resolved context owner");
        }

        String requestedReceiverRole = normalizeOptionalValue(receiverRole);
        if (requestedReceiverRole != null && !actualReceiver.userType().name().equalsIgnoreCase(requestedReceiverRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverRole does not match the resolved context owner");
        }
    }

    private void validateListingAvailability(CropListing cropListing) {
        String status = normalizeOptionalValue(cropListing.getStatus());
        if (status != null && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active crop listings can be used for connection requests");
        }
    }

    private void validateDemandAvailability(BuyerDemands demand) {
        String status = normalizeOptionalValue(demand.getStatus());
        if (status != null && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active buyer demands can be used for connection requests");
        }
    }

    private Optional<Connection> findExistingConnection(
            String senderId,
            String senderRole,
            String receiverId,
            String receiverRole
    ) {
        String pairKey = Connection.buildPairKey(senderId, senderRole, receiverId, receiverRole);
        return connectionRepository.findByPairKey(pairKey)
                .or(() -> connectionRepository.findFirstBySenderIdAndReceiverIdOrSenderIdAndReceiverId(
                        senderId,
                        receiverId,
                        receiverId,
                        senderId
                ));
    }

    private Connection prepareConnection(Connection connection) {
        if (connection == null) {
            return null;
        }

        boolean changed = false;

        String originalStatus = connection.getStatus();
        connection.normalizeStatus();
        changed = changed || !Objects.equals(originalStatus, connection.getStatus());

        if ((connection.getRequestedByUserId() == null || connection.getRequestedByUserId().isBlank())
                && connection.getSenderId() != null && !connection.getSenderId().isBlank()) {
            connection.setRequestedByUserId(connection.getSenderId());
            changed = true;
        }

        if (connection.getRequestedByUserType() == null
                && connection.getSenderRole() != null && !connection.getSenderRole().isBlank()) {
            connection.setRequestedByUserType(parseUserType(connection.getSenderRole(), "senderRole"));
            changed = true;
        }

        if ((connection.getPairKey() == null || connection.getPairKey().isBlank())
                && connection.getSenderId() != null && connection.getReceiverId() != null
                && connection.getSenderRole() != null && connection.getReceiverRole() != null) {
            connection.setPairKey(Connection.buildPairKey(
                    connection.getSenderId(),
                    connection.getSenderRole(),
                    connection.getReceiverId(),
                    connection.getReceiverRole()
            ));
            changed = true;
        }

        if (connection.getParticipantKeys() == null || connection.getParticipantKeys().isEmpty()) {
            if (connection.getSenderId() != null && connection.getReceiverId() != null
                    && connection.getSenderRole() != null && connection.getReceiverRole() != null) {
                connection.setParticipantKeys(buildParticipantKeys(
                        simpleUser(connection.getSenderId(), parseUserType(connection.getSenderRole(), "senderRole")),
                        simpleUser(connection.getReceiverId(), parseUserType(connection.getReceiverRole(), "receiverRole"))
                ));
                changed = true;
            }
        }

        if (connection.getParticipants() == null || connection.getParticipants().isEmpty()) {
            if (connection.getSenderId() != null && connection.getReceiverId() != null
                    && connection.getSenderRole() != null && connection.getReceiverRole() != null) {
                connection.setParticipants(buildParticipants(
                        simpleUser(connection.getSenderId(), parseUserType(connection.getSenderRole(), "senderRole")),
                        simpleUser(connection.getReceiverId(), parseUserType(connection.getReceiverRole(), "receiverRole"))
                ));
                changed = true;
            }
        }

        if ((connection.getContexts() == null || connection.getContexts().isEmpty())
                && connection.getReferenceType() != null && connection.getReferenceId() != null) {
            Connection.ContextRef context = buildContext(connection.getReferenceType(), connection.getReferenceId());
            if (context != null) {
                connection.addContext(context);
                changed = true;
            }
        }

        if (changed) {
            connection.setUpdatedAt(LocalDateTime.now());
            connection.syncLegacyFields();
            return connectionRepository.save(connection);
        }

        return connection;
    }

    private void refreshParticipants(Connection connection, AuthenticatedUser sender, AuthenticatedUser receiver) {
        connection.setPairKey(Connection.buildPairKey(
                sender.userId(),
                sender.userType().name(),
                receiver.userId(),
                receiver.userType().name()
        ));
        connection.setParticipantKeys(buildParticipantKeys(sender, receiver));
        connection.setParticipants(buildParticipants(sender, receiver));
    }

    private List<Connection> deduplicateAndPrepare(List<Connection> connections) {
        List<Connection> prepared = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();

        for (Connection connection : connections) {
            Connection safeConnection = prepareConnection(connection);
            if (safeConnection.getId() != null && !seenIds.add(safeConnection.getId())) {
                continue;
            }
            prepared.add(safeConnection);
        }

        prepared.sort(Comparator.comparing(Connection::getCreatedAt).reversed());
        return prepared;
    }

    private List<AuthenticatedUser> resolveAuthenticatedUsers(String authenticatedEmail) {
        String normalizedEmail = requireValue(authenticatedEmail, "authenticatedEmail").toLowerCase(Locale.ROOT);
        List<AuthenticatedUser> users = new ArrayList<>();

        buyerRepository.findByEmail(normalizedEmail)
                .ifPresent(buyer -> users.add(mapBuyer(buyer)));

        farmerRepository.findByEmail(normalizedEmail)
                .ifPresent(farmer -> users.add(mapFarmer(farmer)));

        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found");
        }

        return users;
    }

    private AuthenticatedUser mapBuyer(Buyer buyer) {
        Buyer.CompanyAddress address = buyer.getCompanyAddress();
        return new AuthenticatedUser(
                buyer.getId(),
                Connection.UserType.BUYER,
                buyer.getName(),
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null
        );
    }

    private AuthenticatedUser mapFarmer(Farmer farmer) {
        Farmer.FarmerAddress address = farmer.getFarmerAddress();
        return new AuthenticatedUser(
                farmer.getId(),
                Connection.UserType.FARMER,
                farmer.getName(),
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null
        );
    }

    private AuthenticatedUser simpleUser(String userId, Connection.UserType userType) {
        return new AuthenticatedUser(userId, userType, null, null, null);
    }

    private Connection.ContextRef requireContext(String referenceType, String referenceId) {
        Connection.ContextRef context = buildContext(referenceType, referenceId);
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceType and referenceId are required");
        }
        return context;
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

    private Connection.UserType parseUserType(String value, String fieldName) {
        try {
            return Connection.UserType.from(requireValue(value, fieldName));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + value);
        }
    }

    private Connection.ContextRef buildContext(String referenceType, String referenceId) {
        if ((referenceType == null || referenceType.isBlank()) && (referenceId == null || referenceId.isBlank())) {
            return null;
        }

        if (referenceType == null || referenceType.isBlank() || referenceId == null || referenceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceType and referenceId must both be provided");
        }

        try {
            return Connection.ContextRef.builder()
                    .type(Connection.ContextType.from(referenceType))
                    .refId(referenceId.trim())
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid referenceType: " + referenceType);
        }
    }

    private List<String> buildParticipantKeys(AuthenticatedUser firstUser, AuthenticatedUser secondUser) {
        List<String> participantKeys = new ArrayList<>();
        participantKeys.add(Connection.buildParticipantKey(firstUser.userId(), firstUser.userType().name()));
        participantKeys.add(Connection.buildParticipantKey(secondUser.userId(), secondUser.userType().name()));
        participantKeys.sort(String::compareTo);
        return participantKeys;
    }

    private List<Connection.ParticipantSnapshot> buildParticipants(AuthenticatedUser firstUser, AuthenticatedUser secondUser) {
        List<Connection.ParticipantSnapshot> participants = new ArrayList<>();
        participants.add(Connection.ParticipantSnapshot.builder()
                .userId(firstUser.userId())
                .userType(firstUser.userType())
                .displayName(firstUser.displayName())
                .city(firstUser.city())
                .state(firstUser.state())
                .build());
        participants.add(Connection.ParticipantSnapshot.builder()
                .userId(secondUser.userId())
                .userType(secondUser.userType())
                .displayName(secondUser.displayName())
                .city(secondUser.city())
                .state(secondUser.state())
                .build());
        participants.sort(Comparator.comparing(participant -> participant.getUserType().name() + ":" + participant.getUserId()));
        return participants;
    }

    private record AuthenticatedUser(
            String userId,
            Connection.UserType userType,
            String displayName,
            String city,
            String state
    ) {
    }

    private record ResolvedContext(
            Connection.ContextRef context,
            AuthenticatedUser receiver
    ) {
    }
}
