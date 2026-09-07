package com.alfaizunawebid.payment.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alfaizunawebid.payment.exception.InvalidSignatureException;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@NoArgsConstructor
@AllArgsConstructor
public class HmacSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${payment.webhook.secret-key}")
    private String secretKey;

    public void validateSignature(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Missing X-Signature header in webhook request");
            throw new InvalidSignatureException("Missing signature header: X-Signature is required");
        }

        String calculatedSignature = calculateHmac(rawPayload, secretKey);

        if (!MessageDigest.isEqual(
                calculatedSignature.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
        )) {
            log.warn("HMAC Signature mismatch! Received: [{}], Expected: [{}]", signatureHeader, calculatedSignature);
            throw new InvalidSignatureException("Invalid signature: payload verification failed");
        }

        log.debug("HMAC Signature verified successfully");
    }

    public String calculateHmac(String data, String key) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC signature", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
