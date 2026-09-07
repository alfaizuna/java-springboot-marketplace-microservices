package com.alfaizunawebid.order.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alfaizunawebid.order.dto.CreateOrderRequest;
import com.alfaizunawebid.order.model.Order;
import com.alfaizunawebid.order.model.OrderStatus;
import com.alfaizunawebid.order.repository.OrderRepository;
import com.alfaizunawebid.order.security.HmacSignatureValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final HmacSignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    @Value("${payment.webhook.secret-key:super-secret-webhook-key-change-in-prod}")
    private String webhookSecretKey;

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = Order.builder()
                .orderNumber(request.getOrderNumber())
                .amount(request.getAmount())
                .status(OrderStatus.PENDING)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Created new order: [{}] with amount [{}]", saved.getOrderNumber(), saved.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{orderNumber}/status")
    public ResponseEntity<?> getOrderStatus(@PathVariable String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .map(order -> ResponseEntity.ok(Map.of(
                        "orderNumber", order.getOrderNumber(),
                        "amount", order.getAmount(),
                        "status", order.getStatus(),
                        "updatedAt", order.getUpdatedAt()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "message", "Order not found"
                )));
    }

    @PostMapping("/{orderNumber}/simulate-webhook")
    public ResponseEntity<?> simulateWebhook(@PathVariable String orderNumber) throws JsonProcessingException {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElse(null);

        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found"));
        }

        Map<String, Object> mockPayload = Map.of(
                "transaction_id", "TRX-" + UUID.randomUUID().toString().substring(0, 8),
                "order_number", order.getOrderNumber(),
                "gross_amount", order.getAmount(),
                "payment_type", "qris",
                "transaction_status", "settlement"
        );

        String rawJson = objectMapper.writeValueAsString(mockPayload);
        String validSignature = signatureValidator.calculateHmac(rawJson, webhookSecretKey);

        return ResponseEntity.ok(Map.of(
                "description", "Use the values below to test POST /api/v1/webhooks/payment",
                "header_name", "X-Signature",
                "valid_signature", validSignature,
                "raw_payload", rawJson
        ));
    }
}
