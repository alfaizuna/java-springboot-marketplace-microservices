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

import com.alfaizunawebid.order.client.ProductServiceClient;
import com.alfaizunawebid.order.dto.CreateOrderRequest;
import com.alfaizunawebid.order.dto.OrderResponse;
import com.alfaizunawebid.order.dto.StockCheckResponse;
import com.alfaizunawebid.order.exception.InsufficientStockException;
import com.alfaizunawebid.order.model.Order;
import com.alfaizunawebid.order.model.OrderStatus;
import com.alfaizunawebid.order.repository.OrderRepository;
import com.alfaizunawebid.order.security.HmacSignatureValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management with product stock verification")
public class OrderController {

    private final OrderRepository orderRepository;
    private final HmacSignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;
    private final ProductServiceClient productServiceClient;

    @Value("${payment.webhook.secret-key:super-secret-webhook-key-change-in-prod}")
    private String webhookSecretKey;

    /**
     * POST /api/v1/orders
     * Membuat order baru. Sebelum menyimpan, verifikasi stok ke product-service.
     * Flow:
     *   1. Validasi request body
     *   2. Cek stok ke product-service via REST call
     *   3. Jika stok cukup → simpan order dengan status PENDING
     *   4. Jika stok tidak cukup → throw InsufficientStockException (HTTP 422)
     */
    @PostMapping
    @Operation(summary = "Create a new order",
            description = "Creates an order after verifying product stock availability with product-service.")
    @ApiResponse(responseCode = "201", description = "Order created successfully")
    @ApiResponse(responseCode = "404", description = "Product SKU not found in catalog")
    @ApiResponse(responseCode = "422", description = "Insufficient stock for the requested quantity")
    @ApiResponse(responseCode = "503", description = "Product service unavailable")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Received order request: orderNumber={}, sku={}, qty={}",
                request.getOrderNumber(), request.getProductSku(), request.getQuantity());

        // Step 1: Verifikasi stok ke product-service
        StockCheckResponse stockCheck = productServiceClient.checkStock(
                request.getProductSku(), request.getQuantity());

        // Step 2: Tolak jika stok tidak cukup
        if (stockCheck == null || !stockCheck.isSufficient()) {
            int available = stockCheck != null ? stockCheck.getAvailableStock() : 0;
            throw new InsufficientStockException(request.getProductSku(), request.getQuantity(), available);
        }

        // Step 3: Simpan order
        Order order = Order.builder()
                .orderNumber(request.getOrderNumber())
                .productSku(request.getProductSku())
                .quantity(request.getQuantity())
                .amount(request.getAmount())
                .status(OrderStatus.PENDING)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Created order [{}] for SKU={} qty={} amount={}",
                saved.getOrderNumber(), saved.getProductSku(), saved.getQuantity(), saved.getAmount());

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(saved));
    }

    /**
     * GET /api/v1/orders/{orderNumber}/status
     */
    @GetMapping("/{orderNumber}/status")
    @Operation(summary = "Get order status", description = "Returns current status and details of an order.")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<?> getOrderStatus(@PathVariable String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .map(order -> ResponseEntity.ok(Map.of(
                        "orderNumber", order.getOrderNumber(),
                        "productSku", order.getProductSku(),
                        "quantity", order.getQuantity(),
                        "amount", order.getAmount(),
                        "status", order.getStatus(),
                        "updatedAt", order.getUpdatedAt()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "message", "Order not found"
                )));
    }

    /**
     * POST /api/v1/orders/{orderNumber}/simulate-webhook
     * Helper untuk generate payload + HMAC signature yang valid untuk testing webhook.
     */
    @PostMapping("/{orderNumber}/simulate-webhook")
    @Operation(summary = "Simulate payment webhook",
            description = "Generates a valid HMAC-signed payload to test the payment webhook endpoint.")
    public ResponseEntity<?> simulateWebhook(@PathVariable String orderNumber) throws JsonProcessingException {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);

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
