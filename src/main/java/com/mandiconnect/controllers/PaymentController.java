package com.mandiconnect.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mandiconnect.services.PaymentService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    @PostMapping("/create-order")
    public ResponseEntity<?> createPaymentOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreatePaymentOrderRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(paymentService.createPaymentOrder(email, body.orderId()));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody VerifyPaymentRequest body
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(paymentService.verifyPayment(
                email,
                new PaymentService.VerifyPaymentCommand(
                        body.orderId(),
                        body.razorpayOrderId(),
                        body.razorpayPaymentId(),
                        body.razorpaySignature()
                )
        ));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getPaymentsForOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String email = extractAuthenticatedEmail(authHeader);
        return ResponseEntity.ok(paymentService.getPaymentsForOrder(orderId, email));
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
    public record CreatePaymentOrderRequest(String orderId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifyPaymentRequest(
            String orderId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {
    }
}
