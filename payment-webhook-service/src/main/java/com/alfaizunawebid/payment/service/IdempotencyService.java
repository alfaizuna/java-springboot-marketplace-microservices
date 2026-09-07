package com.alfaizunawebid.payment.service;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "payment:idempotency:";
    private static final Duration LOCK_EXPIRATION = Duration.ofMinutes(5);
    private static final Duration COMPLETED_EXPIRATION = Duration.ofDays(7);

    public boolean acquireLock(String transactionId) {
        String key = KEY_PREFIX + transactionId;
        Boolean isSet = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", LOCK_EXPIRATION);
        boolean acquired = Boolean.TRUE.equals(isSet);

        if (acquired) {
            log.info("Acquired idempotency lock for transaction [{}]", transactionId);
        } else {
            log.warn("Failed to acquire idempotency lock for transaction [{}]. Duplicate callback detected.", transactionId);
        }

        return acquired;
    }

    public void markAsCompleted(String transactionId) {
        String key = KEY_PREFIX + transactionId;
        redisTemplate.opsForValue().set(key, "COMPLETED", COMPLETED_EXPIRATION);
        log.info("Marked transaction [{}] as COMPLETED in idempotency store", transactionId);
    }

    public void releaseLock(String transactionId) {
        String key = KEY_PREFIX + transactionId;
        redisTemplate.delete(key);
        log.info("Released idempotency lock for transaction [{}]", transactionId);
    }
}
