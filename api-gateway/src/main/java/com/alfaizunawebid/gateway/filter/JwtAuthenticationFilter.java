package com.alfaizunawebid.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.alfaizunawebid.gateway.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Centralized GlobalFilter untuk validasi token JWT (RS256) di API Gateway.
 * Memeriksa signature menggunakan Public Key Auth Service dan menginjeksikan
 * user claims ke downstream request headers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final PublicKey publicKey;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // Endpoint publik yang tidak memerlukan validasi token di Gateway
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",
            "/api/v1/webhooks",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. Bypass untuk endpoint publik
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        // 2. Periksa keberadaan header Authorization: Bearer <token>
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Unauthorized request to protected path [{}]: Missing or invalid Authorization header", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // 3. Verifikasi tanda tangan JWT dengan RSA Public Key
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            log.debug("JWT verified for user [{}] with role [{}]", username, role);

            // 4. Mutasi request: Tambahkan custom headers untuk downstream services
            ServerHttpRequest.Builder mutatedRequestBuilder = request.mutate()
                    .header("X-User-Email", username != null ? username : "")
                    .header("X-User-Role", role != null ? role : "");

            return chain.filter(exchange.mutate().request(mutatedRequestBuilder.build()).build());

        } catch (ExpiredJwtException e) {
            log.warn("Token expired for request to [{}]: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token has expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT signature or format for request to [{}]: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid JWT token signature or format");
        } catch (Exception e) {
            log.error("Unexpected error validating JWT token: {}", e.getMessage(), e);
            return onError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Authentication processing error");
        }
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // Prioritas tinggi agar dieksekusi sebelum routing
    }
}
