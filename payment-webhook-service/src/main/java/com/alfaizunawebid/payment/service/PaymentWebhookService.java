package com.alfaizunawebid.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alfaizunawebid.payment.dto.WebhookPayload;
import com.alfaizunawebid.payment.model.PaymentTransactionLog;
import com.alfaizunawebid.payment.repository.PaymentTransactionLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentTransactionLogRepository logRepository;

    @Transactional
    public void processPaymentWebhook(WebhookPayload payload, String rawPayload) {
        log.info("Recording payment webhook transaction [{}] for order [{}] with status [{}]",
                payload.getTransactionId(), payload.getOrderNumber(), payload.getTransactionStatus());

        PaymentTransactionLog auditLog = PaymentTransactionLog.builder()
                .transactionId(payload.getTransactionId())
                .orderNumber(payload.getOrderNumber())
                .paymentType(payload.getPaymentType())
                .grossAmount(payload.getGrossAmount())
                .transactionStatus(payload.getTransactionStatus())
                .rawPayload(rawPayload)
                .build();

        logRepository.save(auditLog);
        log.info("Successfully recorded audit log for transaction [{}]", payload.getTransactionId());
    }
}
