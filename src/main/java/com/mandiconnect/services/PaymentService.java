package com.mandiconnect.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mandiconnect.models.Connection;
import com.mandiconnect.models.Order;
import com.mandiconnect.models.PaymentTransaction;
import com.mandiconnect.repositories.BuyerRepository;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.repositories.OrderRepository;
import com.mandiconnect.repositories.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final BuyerRepository buyerRepository;
    private final FarmerRepository farmerRepository;
    private final NotificationService notificationService;
    private final ChatService chatService;

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.api-base-url:https://api.razorpay.com/v1}")
    private String razorpayApiBaseUrl;

    @Value("${razorpay.currency:INR}")
    private String razorpayDefaultCurrency;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentOrderResponse createPaymentOrder(String authenticatedEmail, String orderId, String returnUrl) {
        ensureRazorpayConfigured();
        String safeReturnUrl = requireValue(returnUrl, "returnUrl");

        Order order = getOrderForUpdate(orderId);
        AuthenticatedUser actor = resolveOrderBuyer(order, authenticatedEmail);

        validateOrderReadyForPayment(order);

        PaymentTransaction reusable = findReusableInitiatedPayment(order);
        if (reusable != null) {
            return buildPaymentOrderResponse(order, reusable, safeReturnUrl);
        }

        long amountInSubunits = toSubunits(order.getTotalAmount(), order.getCurrency());
        String currency = normalizeCurrency(order.getCurrency());
        String receipt = order.getOrderCode();
        LocalDateTime now = LocalDateTime.now();

        JsonNode gatewayOrder = createGatewayOrder(order, amountInSubunits, currency, receipt);
        String gatewayOrderId = requireJsonText(gatewayOrder, "id", "Razorpay order id missing");

        PaymentTransaction payment = PaymentTransaction.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .connectionId(order.getConnectionId())
                .chatId(order.getChatId())
                .buyerId(order.getBuyerId())
                .farmerId(order.getFarmerId())
                .gateway(PaymentTransaction.Gateway.RAZORPAY.name())
                .amount(order.getTotalAmount())
                .amountInSubunits(amountInSubunits)
                .currency(currency)
                .status(PaymentTransaction.TransactionStatus.INITIATED.name())
                .gatewayOrderId(gatewayOrderId)
                .gatewayStatus(optionalJsonText(gatewayOrder, "status"))
                .receipt(receipt)
                .initiatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        PaymentTransaction savedPayment = paymentTransactionRepository.save(payment);

        updateOrderForPaymentInitiated(order, actor, now);
        Order savedOrder = orderRepository.save(order);
        emitPaymentInitiatedSideEffects(savedOrder, actor);

        return buildPaymentOrderResponse(savedOrder, savedPayment, safeReturnUrl);
    }

    public PaymentVerificationResponse verifyPayment(String authenticatedEmail, VerifyPaymentCommand command) {
        ensureRazorpayConfigured();

        String orderId = requireValue(command.orderId(), "orderId");
        String razorpayOrderId = requireValue(command.razorpayOrderId(), "razorpayOrderId");
        String razorpayPaymentId = requireValue(command.razorpayPaymentId(), "razorpayPaymentId");
        String razorpaySignature = requireValue(command.razorpaySignature(), "razorpaySignature");

        Order order = getOrderForUpdate(orderId);
        AuthenticatedUser actor = resolveOrderBuyer(order, authenticatedEmail);

        PaymentTransaction payment = paymentTransactionRepository.findByGatewayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found for Razorpay order"));

        if (!Objects.equals(payment.getOrderId(), order.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay order does not belong to the provided order");
        }

        if (PaymentTransaction.TransactionStatus.SUCCESS.name().equals(payment.getStatus())
                && Objects.equals(payment.getGatewayPaymentId(), razorpayPaymentId)) {
            return new PaymentVerificationResponse(true, order, payment, "Payment already verified");
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setGatewayPaymentId(razorpayPaymentId);
        payment.setGatewaySignature(razorpaySignature);
        payment.setUpdatedAt(now);

        if (!isValidRazorpaySignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            markPaymentFailed(payment, order, actor, now, "INVALID_SIGNATURE", "Invalid Razorpay signature");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay signature");
        }

        JsonNode gatewayPayment = fetchGatewayPayment(razorpayPaymentId);
        String gatewayStatus = normalizeOptionalValue(optionalJsonText(gatewayPayment, "status"));

        if ("authorized".equalsIgnoreCase(gatewayStatus)) {
            gatewayPayment = captureGatewayPayment(razorpayPaymentId, payment.getAmountInSubunits(), payment.getCurrency());
            gatewayStatus = normalizeOptionalValue(optionalJsonText(gatewayPayment, "status"));
        }

        if ("captured".equalsIgnoreCase(gatewayStatus)) {
            payment.setStatus(PaymentTransaction.TransactionStatus.SUCCESS.name());
            payment.setGatewayStatus(gatewayStatus);
            payment.setFailureCode(null);
            payment.setFailureDescription(null);
            payment.setVerifiedAt(now);
            payment.setPaidAt(now);
            payment.setUpdatedAt(now);
            PaymentTransaction savedPayment = paymentTransactionRepository.save(payment);

            updateOrderForPaymentSuccess(order, actor, now);
            Order savedOrder = orderRepository.save(order);
            emitPaymentSuccessSideEffects(savedOrder, savedPayment, actor);

            return new PaymentVerificationResponse(true, savedOrder, savedPayment, "Payment verified successfully");
        }

        String failureCode = firstNonBlank(
                optionalJsonText(gatewayPayment, "error_code"),
                optionalJsonText(gatewayPayment, "status"),
                "PAYMENT_NOT_CAPTURED"
        );
        String failureDescription = firstNonBlank(
                optionalJsonText(gatewayPayment, "error_description"),
                optionalJsonText(gatewayPayment, "error_reason"),
                "Razorpay payment was not captured"
        );

        markPaymentFailed(payment, order, actor, now, failureCode, failureDescription);
        return new PaymentVerificationResponse(false, order, payment, failureDescription);
    }

    public OrderPaymentsResponse getPaymentsForOrder(String orderId, String authenticatedEmail) {
        Order order = getOrderForUpdate(orderId);
        authorizeOrderAccess(order, authenticatedEmail);

        List<PaymentTransaction> payments = paymentTransactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        PaymentTransaction latestPayment = payments.isEmpty() ? null : payments.get(0);
        return new OrderPaymentsResponse(order, latestPayment, payments);
    }

    private PaymentOrderResponse buildPaymentOrderResponse(Order order, PaymentTransaction payment, String returnUrl) {
        return new PaymentOrderResponse(
                order,
                payment,
                new RazorpayCheckout(
                        razorpayKeyId,
                        payment.getGatewayOrderId(),
                        payment.getAmountInSubunits(),
                        payment.getCurrency(),
                        payment.getReceipt(),
                        order.getOrderCode(),
                        buildCheckoutUrl(payment.getId(), returnUrl)
                )
        );
    }

    public String renderHostedCheckoutPage(String paymentId, String returnUrl) {
        ensureRazorpayConfigured();

        PaymentTransaction payment = paymentTransactionRepository.findById(requireValue(paymentId, "paymentId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));
        Order order = getOrderForUpdate(payment.getOrderId());

        if (!PaymentTransaction.TransactionStatus.INITIATED.name().equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment is no longer available for checkout");
        }

        String safeReturnUrl = requireValue(returnUrl, "returnUrl");
        String successUrl = appendQueryParams(
                safeReturnUrl,
                "status", "success",
                "orderId", order.getId(),
                "razorpayOrderId", payment.getGatewayOrderId()
        );
        String cancelUrl = appendQueryParams(
                safeReturnUrl,
                "status", "cancelled",
                "orderId", order.getId(),
                "razorpayOrderId", payment.getGatewayOrderId()
        );
        String failedUrl = appendQueryParams(
                safeReturnUrl,
                "status", "failed",
                "orderId", order.getId(),
                "razorpayOrderId", payment.getGatewayOrderId()
        );

        String buyerName = order.getBuyer() != null ? nullSafe(order.getBuyer().getDisplayName()) : "";
        String buyerContact = order.getBuyer() != null ? sanitizePhone(order.getBuyer().getMobile()) : "";
        String description = order.getItem() != null ? firstNonBlank(order.getItem().getTitle(), "Crop Order") : "Crop Order";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>MandiConnect Payment</title>
                  <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
                  <style>
                    body { font-family: Arial, sans-serif; background: #f3f9f4; color: #111827; margin: 0; display: flex; min-height: 100vh; align-items: center; justify-content: center; }
                    .card { width: min(92vw, 420px); background: #fff; border-radius: 20px; padding: 28px; box-shadow: 0 18px 45px rgba(15, 23, 42, 0.14); text-align: center; }
                    h1 { margin: 0 0 10px; font-size: 24px; }
                    p { margin: 0 0 10px; color: #4b5563; line-height: 1.5; }
                    .amount { margin: 20px 0; font-size: 28px; font-weight: 800; color: #166534; }
                    .btn { width: 100%; border: 0; border-radius: 14px; padding: 14px 16px; font-size: 16px; font-weight: 700; cursor: pointer; }
                    .btn-primary { background: #16a34a; color: #fff; }
                    .btn-secondary { margin-top: 10px; background: #f3f4f6; color: #111827; }
                    .note { margin-top: 16px; font-size: 12px; color: #6b7280; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Complete Payment</h1>
                    <p>Order %s</p>
                    <p>%s</p>
                    <div class="amount">%s %s</div>
                    <button id="pay-btn" class="btn btn-primary">Pay Now</button>
                    <button id="cancel-btn" class="btn btn-secondary">Cancel</button>
                    <p class="note">You will return to the app automatically after payment.</p>
                  </div>

                  <script>
                    const redirect = (url, extraParams = {}) => {
                      const target = new URL(url);
                      Object.entries(extraParams).forEach(([key, value]) => {
                        if (value) target.searchParams.set(key, value);
                      });
                      window.location.replace(target.toString());
                    };

                    const options = {
                      key: %s,
                      amount: %s,
                      currency: %s,
                      name: "MandiConnect",
                      description: %s,
                      order_id: %s,
                      prefill: {
                        name: %s,
                        contact: %s
                      },
                      notes: {
                        orderCode: %s,
                        appOrderId: %s,
                        connectionId: %s,
                        chatId: %s
                      },
                      theme: {
                        color: "#16a34a"
                      },
                      modal: {
                        ondismiss: function () {
                          redirect(%s);
                        }
                      },
                      handler: function (response) {
                        redirect(%s, {
                          razorpayPaymentId: response.razorpay_payment_id,
                          razorpaySignature: response.razorpay_signature,
                          razorpayOrderId: response.razorpay_order_id
                        });
                      }
                    };

                    const checkout = new Razorpay(options);

                    checkout.on("payment.failed", function (response) {
                      redirect(%s, {
                        failureCode: response.error && response.error.code,
                        failureDescription: response.error && response.error.description
                      });
                    });

                    document.getElementById("pay-btn").addEventListener("click", function () {
                      checkout.open();
                    });

                    document.getElementById("cancel-btn").addEventListener("click", function () {
                      redirect(%s);
                    });

                    window.onload = function () {
                      checkout.open();
                    };
                  </script>
                </body>
                </html>
                """.formatted(
                escapeHtml(order.getOrderCode()),
                escapeHtml(description),
                escapeHtml(payment.getCurrency()),
                escapeHtml(trimDecimal(fromSubunits(payment.getAmountInSubunits()))),
                jsString(razorpayKeyId),
                payment.getAmountInSubunits(),
                jsString(payment.getCurrency()),
                jsString(description),
                jsString(payment.getGatewayOrderId()),
                jsString(buyerName),
                jsString(buyerContact),
                jsString(order.getOrderCode()),
                jsString(order.getId()),
                jsString(order.getConnectionId()),
                jsString(order.getChatId()),
                jsString(cancelUrl),
                jsString(successUrl),
                jsString(failedUrl),
                jsString(cancelUrl)
        );
    }

    private PaymentTransaction findReusableInitiatedPayment(Order order) {
        PaymentTransaction latestPayment = paymentTransactionRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElse(null);

        if (latestPayment == null) {
            return null;
        }

        boolean paymentActive = PaymentTransaction.TransactionStatus.INITIATED.name().equals(latestPayment.getStatus())
                && latestPayment.getGatewayOrderId() != null
                && !latestPayment.getGatewayOrderId().isBlank();
        boolean orderAwaitingPayment = Order.PaymentStatus.INITIATED.name().equals(order.getPaymentStatus())
                && Order.OrderStatus.PAYMENT_PENDING.name().equals(order.getStatus());

        return paymentActive && orderAwaitingPayment ? latestPayment : null;
    }

    private void validateOrderReadyForPayment(Order order) {
        Order.OrderStatus status = parseOrderStatus(order.getStatus());
        if (status == Order.OrderStatus.CANCELLED
                || status == Order.OrderStatus.COMPLETED
                || status == Order.OrderStatus.CONFIRMED
                || status == Order.OrderStatus.PROCESSING
                || status == Order.OrderStatus.DISPATCHED
                || status == Order.OrderStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order is no longer eligible for payment creation");
        }

        if (Order.PaymentStatus.SUCCESS.name().equals(order.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order payment is already successful");
        }

        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total amount must be greater than 0");
        }
    }

    private void updateOrderForPaymentInitiated(Order order, AuthenticatedUser actor, LocalDateTime now) {
        order.setStatus(Order.OrderStatus.PAYMENT_PENDING.name());
        order.setPaymentStatus(Order.PaymentStatus.INITIATED.name());
        order.setUpdatedAt(now);
        appendOrderEvent(order, Order.EventType.PAYMENT_PENDING, actor, "Payment initiated through Razorpay", now);
    }

    private void updateOrderForPaymentSuccess(Order order, AuthenticatedUser actor, LocalDateTime now) {
        order.setStatus(Order.OrderStatus.PAID.name());
        order.setPaymentStatus(Order.PaymentStatus.SUCCESS.name());
        order.setUpdatedAt(now);
        appendOrderEvent(order, Order.EventType.PAYMENT_SUCCESS, actor, "Payment verified successfully", now);
    }

    private void markPaymentFailed(
            PaymentTransaction payment,
            Order order,
            AuthenticatedUser actor,
            LocalDateTime now,
            String failureCode,
            String failureDescription
    ) {
        payment.setStatus(PaymentTransaction.TransactionStatus.FAILED.name());
        payment.setGatewayStatus(normalizeOptionalValue(payment.getGatewayStatus()));
        payment.setFailureCode(normalizeOptionalValue(failureCode));
        payment.setFailureDescription(normalizeOptionalValue(failureDescription));
        payment.setVerifiedAt(now);
        payment.setUpdatedAt(now);
        paymentTransactionRepository.save(payment);

        order.setStatus(Order.OrderStatus.PLACED.name());
        order.setPaymentStatus(Order.PaymentStatus.FAILED.name());
        order.setUpdatedAt(now);
        appendOrderEvent(order, Order.EventType.PAYMENT_FAILED, actor, normalizeOptionalValue(failureDescription), now);
        Order savedOrder = orderRepository.save(order);
        emitPaymentFailedSideEffects(savedOrder, payment, actor, failureDescription);
    }

    private void appendOrderEvent(
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

    private AuthenticatedUser resolveOrderBuyer(Order order, String authenticatedEmail) {
        AuthenticatedUser actor = resolveOrderActor(order, authenticatedEmail);
        if (actor.userType() != Connection.UserType.BUYER || !Objects.equals(actor.userId(), order.getBuyerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the buyer can perform this payment action");
        }
        return actor;
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

    private void authorizeOrderAccess(Order order, String authenticatedEmail) {
        resolveOrderActor(order, authenticatedEmail);
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

    private Order getOrderForUpdate(String orderId) {
        return orderRepository.findById(requireValue(orderId, "orderId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private Order.OrderStatus parseOrderStatus(String value) {
        try {
            return Order.OrderStatus.valueOf(requireValue(value, "status").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported order status: " + value);
        }
    }

    private JsonNode createGatewayOrder(Order order, long amountInSubunits, String currency, String receipt) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("amount", amountInSubunits);
            payload.put("currency", currency);
            payload.put("receipt", receipt);

            ObjectNode notes = objectMapper.createObjectNode();
            notes.put("appOrderId", order.getId());
            notes.put("orderCode", order.getOrderCode());
            notes.put("connectionId", nullSafe(order.getConnectionId()));
            notes.put("chatId", nullSafe(order.getChatId()));
            payload.set("notes", notes);

            return sendRazorpayRequest("/orders", "POST", payload);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to create Razorpay order");
        }
    }

    private JsonNode fetchGatewayPayment(String razorpayPaymentId) {
        try {
            return sendRazorpayRequest("/payments/" + requireValue(razorpayPaymentId, "razorpayPaymentId"), "GET", null);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch Razorpay payment");
        }
    }

    private JsonNode captureGatewayPayment(String razorpayPaymentId, Long amountInSubunits, String currency) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("amount", amountInSubunits);
            payload.put("currency", normalizeCurrency(currency));
            return sendRazorpayRequest("/payments/" + requireValue(razorpayPaymentId, "razorpayPaymentId") + "/capture", "POST", payload);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to capture Razorpay payment");
        }
    }

    private JsonNode sendRazorpayRequest(String path, String method, JsonNode body) throws Exception {
        String requestBody = body == null ? null : objectMapper.writeValueAsString(body);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(normalizeApiBaseUrl() + path))
                .header("Authorization", "Basic " + basicAuthToken())
                .header("Accept", "application/json");

        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody == null ? "{}" : requestBody));
        } else {
            requestBuilder.GET();
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = extractRazorpayErrorDetail(response.body());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail);
        }

        return objectMapper.readTree(response.body());
    }

    private boolean isValidRazorpaySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        try {
            String payload = razorpayOrderId + "|" + razorpayPaymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedSignature = bytesToHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    razorpaySignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to verify Razorpay signature");
        }
    }

    private long toSubunits(Double amount, String currency) {
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than 0");
        }

        if (!"INR".equalsIgnoreCase(normalizeCurrency(currency))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only INR payments are supported");
        }

        try {
            return BigDecimal.valueOf(amount)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment amount");
        }
    }

    private String basicAuthToken() {
        String credentials = razorpayKeyId + ":" + razorpayKeySecret;
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void ensureRazorpayConfigured() {
        if (normalizeOptionalValue(razorpayKeyId) == null || normalizeOptionalValue(razorpayKeySecret) == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is not configured");
        }
    }

    private String normalizeApiBaseUrl() {
        String safeBaseUrl = requireValue(razorpayApiBaseUrl, "razorpay.api-base-url");
        return safeBaseUrl.endsWith("/") ? safeBaseUrl.substring(0, safeBaseUrl.length() - 1) : safeBaseUrl;
    }

    private String normalizeCurrency(String currency) {
        String normalized = normalizeOptionalValue(currency);
        if (normalized == null) {
            normalized = normalizeOptionalValue(razorpayDefaultCurrency);
        }
        return normalized == null ? "INR" : normalized.toUpperCase(Locale.ROOT);
    }

    private String extractRazorpayErrorDetail(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            String description = optionalJsonText(error, "description");
            if (description != null) {
                return "Razorpay request failed: " + description;
            }
        } catch (Exception ignored) {
        }
        return "Razorpay request failed";
    }

    private String requireJsonText(JsonNode node, String fieldName, String errorMessage) {
        String value = optionalJsonText(node, fieldName);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, errorMessage);
        }
        return value;
    }

    private String optionalJsonText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String text = field.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String nullSafe(String value) {
        String safeValue = normalizeOptionalValue(value);
        return safeValue == null ? "" : safeValue;
    }

    private String buildCheckoutUrl(String paymentId, String returnUrl) {
        return normalizeApiBaseUrl()
                + "/payments/checkout/"
                + requireValue(paymentId, "paymentId")
                + "?returnUrl="
                + urlEncode(returnUrl);
    }

    private String appendQueryParams(String baseUrl, String... keyValuePairs) {
        StringBuilder builder = new StringBuilder(requireValue(baseUrl, "baseUrl"));
        builder.append(baseUrl.contains("?") ? "&" : "?");

        for (int index = 0; index < keyValuePairs.length; index += 2) {
            if (index > 0) {
                builder.append("&");
            }
            builder.append(urlEncode(keyValuePairs[index]))
                    .append("=")
                    .append(urlEncode(keyValuePairs[index + 1]));
        }

        return builder.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(requireValue(value, "value"), StandardCharsets.UTF_8);
    }

    private String jsString(String value) {
        String safeValue = value == null ? "" : value;
        return "\"" + safeValue
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "") + "\"";
    }

    private String escapeHtml(String value) {
        String safeValue = value == null ? "" : value;
        return safeValue
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String sanitizePhone(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String digits = value.replaceAll("[^\\d+]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        return digits;
    }

    private Double fromSubunits(Long amountInSubunits) {
        if (amountInSubunits == null) {
            return null;
        }
        return BigDecimal.valueOf(amountInSubunits)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String trimDecimal(Double value) {
        if (value == null) {
            return null;
        }

        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        String text = decimal.toPlainString();
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String safeValue = normalizeOptionalValue(value);
            if (safeValue != null) {
                return safeValue;
            }
        }
        return null;
    }

    private void emitPaymentInitiatedSideEffects(Order order, AuthenticatedUser actor) {
        sendPaymentSystemMessage(order, "Payment initiated for order " + order.getOrderCode() + ".", actor.userId());
    }

    private void emitPaymentSuccessSideEffects(Order order, PaymentTransaction payment, AuthenticatedUser actor) {
        sendPaymentSystemMessage(order, "Payment successful for order " + order.getOrderCode() + ".", actor.userId());
        notifySafely(() -> notificationService.notifyPaymentSuccess(order, payment));
    }

    private void emitPaymentFailedSideEffects(Order order, PaymentTransaction payment, AuthenticatedUser actor, String failureDescription) {
        String suffix = normalizeOptionalValue(failureDescription);
        String text = "Payment failed for order " + order.getOrderCode() + (suffix != null ? ": " + suffix : ".");
        sendPaymentSystemMessage(order, text, actor.userId());
        notifySafely(() -> notificationService.notifyPaymentFailed(order, payment));
    }

    private void sendPaymentSystemMessage(Order order, String text, String actedByUserId) {
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

    private record AuthenticatedUser(String userId, Connection.UserType userType) {
    }

    public record CreatePaymentOrderCommand(String orderId) {
    }

    public record VerifyPaymentCommand(
            String orderId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {
    }

    public record RazorpayCheckout(
            String keyId,
            String razorpayOrderId,
            Long amount,
            String currency,
            String receipt,
            String orderCode,
            String checkoutUrl
    ) {
    }

    public record PaymentOrderResponse(
            Order order,
            PaymentTransaction payment,
            RazorpayCheckout checkout
    ) {
    }

    public record PaymentVerificationResponse(
            boolean verified,
            Order order,
            PaymentTransaction payment,
            String message
    ) {
    }

    public record OrderPaymentsResponse(
            Order order,
            PaymentTransaction latestPayment,
            List<PaymentTransaction> payments
    ) {
    }
}
