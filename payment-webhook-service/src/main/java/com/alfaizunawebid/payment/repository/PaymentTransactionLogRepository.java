package com.alfaizunawebid.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alfaizunawebid.payment.model.PaymentTransactionLog;

public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLog, Long> {
    Optional<PaymentTransactionLog> findByTransactionId(String transactionId);
}
