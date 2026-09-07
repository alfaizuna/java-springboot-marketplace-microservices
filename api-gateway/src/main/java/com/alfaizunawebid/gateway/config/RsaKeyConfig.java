package com.alfaizunawebid.gateway.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import lombok.extern.slf4j.Slf4j;

/**
 * Konfigurasi untuk memuat RSA Public Key di API Gateway.
 * Gateway hanya membutuhkan Public Key untuk memvalidasi tanda tangan JWT token,
 * tanpa pernah memegang Private Key (Prinsip Asymmetric Security).
 */
@Configuration
@Slf4j
public class RsaKeyConfig {

    @Value("${application.security.jwt.rsa.public-key-location}")
    private Resource publicKeyResource;

    @Bean
    public PublicKey rsaPublicKey() throws Exception {
        try (InputStream is = publicKeyResource.getInputStream()) {
            String keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String publicKeyPEM = keyContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            log.info("API Gateway: RSA Public Key loaded successfully from: {}", publicKeyResource.getDescription());
            return keyFactory.generatePublic(keySpec);
        }
    }
}
