package com.mandiconnect.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mandiconnect.services.OrderService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateOrderRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.createOrder(
                email,
                new OrderService.CreateOrderCommand(
                        body.connectionId(),
                        body.chatId(),
                        body.cropListingId(),
                        body.quantity(),
                        body.unit(),
                        body.currency(),
                        body.notes(),
                        body.deliveryDetails() == null
                                ? null
                                : new OrderService.DeliveryDetailsInput(
                                body.deliveryDetails().contactName(),
                                body.deliveryDetails().contactPhone(),
                                body.deliveryDetails().addressLine1(),
                                body.deliveryDetails().addressLine2(),
                                body.deliveryDetails().village(),
                                body.deliveryDetails().city(),
                                body.deliveryDetails().state(),
                                body.deliveryDetails().country(),
                                body.deliveryDetails().pincode(),
                                body.deliveryDetails().landmark(),
                                body.deliveryDetails().preferredDeliveryDate(),
                                body.deliveryDetails().preferredTimeSlot(),
                                body.deliveryDetails().instructions()
                        )
                )
        ));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.getOrderById(orderId, email));
    }

    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<?> getBuyerOrders(
            @PathVariable String buyerId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.getBuyerOrders(buyerId, email));
    }

    @GetMapping("/farmer/{farmerId}")
    public ResponseEntity<?> getFarmerOrders(
            @PathVariable String farmerId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.getFarmerOrders(farmerId, email));
    }

    @GetMapping("/connection/{connectionId}")
    public ResponseEntity<?> getOrdersByConnection(
            @PathVariable String connectionId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.getOrdersByConnection(connectionId, email));
    }

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<?> getOrdersByChat(
            @PathVariable String chatId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.getOrdersByChat(chatId, email));
    }

    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirmOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) OrderActionRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.confirmOrder(orderId, email, body != null ? body.note() : null));
    }

    @PatchMapping("/{orderId}/processing")
    public ResponseEntity<?> markOrderProcessing(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) OrderActionRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.markOrderProcessing(orderId, email, body != null ? body.note() : null));
    }

    @PatchMapping("/{orderId}/dispatch")
    public ResponseEntity<?> dispatchOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) OrderActionRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.dispatchOrder(orderId, email, body != null ? body.note() : null));
    }

    @PatchMapping("/{orderId}/deliver")
    public ResponseEntity<?> deliverOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) OrderActionRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.deliverOrder(orderId, email, body != null ? body.note() : null));
    }

    @PatchMapping("/{orderId}/complete")
    public ResponseEntity<?> completeOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) OrderActionRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.completeOrder(orderId, email, body != null ? body.note() : null));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) OrderActionRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(orderService.cancelOrder(orderId, email, body != null ? body.note() : null));
    }

    private String extractAuthenticatedEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        return jwtUtil.getEmailFromToken(token);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateOrderRequest(
            String connectionId,
            String chatId,
            String cropListingId,
            Double quantity,
            String unit,
            String currency,
            String notes,
            DeliveryDetailsRequest deliveryDetails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryDetailsRequest(
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
            LocalDate preferredDeliveryDate,
            String preferredTimeSlot,
            String instructions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderActionRequest(String note) {
    }
}
