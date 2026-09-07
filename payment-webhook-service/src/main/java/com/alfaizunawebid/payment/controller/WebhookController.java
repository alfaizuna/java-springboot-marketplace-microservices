package com.alfaizunawebid.payment.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alfaizunawebid.payment.dto.WebhookPayload;
import com.alfaizunawebid.payment.security.HmacSignatureValidator;
import com.alfaizunawebid.payment.service.IdempotencyService;
import com.alfaizunawebid.payment.service.PaymentWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final HmacSignatureValidator signatureValidator;
    private final IdempotencyService idempotencyService;
    private final PaymentWebhookService paymentWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawPayload
    ) {
        log.info("Received payment webhook notification");

        // 1. Verifikasi HMAC Signature
        signatureValidator.validateSignature(rawPayload, signature);

        // 2. Parse raw JSON string ke DTO WebhookPayload
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WebhookPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook JSON payload", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Malformed JSON payload"
            ));
        }

        String transactionId = payload.getTransactionId();

        // 3. Redis Idempotency Check
        boolean lockAcquired = idempotencyService.acquireLock(transactionId);
        if (!lockAcquired) {
            log.info("Duplicate webhook callback received for transaction [{}]. Returning 200 OK immediately.",
                    transactionId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Duplicate notification ignored"
            ));
        }

        // 4. Proses pencatatan audit log & bisnis payment
        try {
            paymentWebhookService.processPaymentWebhook(payload, rawPayload);
            idempotencyService.markAsCompleted(transactionId);
        } catch (Exception e) {
            log.error("Internal error processing webhook for transaction [{}]. Releasing lock for retry.",
                    transactionId, e);
            idempotencyService.releaseLock(transactionId);
            throw e;
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Payment webhook processed successfully"
        ));
    }
}
