package com.alfaizunawebid.gateway.filter;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();

        filter = new JwtAuthenticationFilter(keyPair.getPublic());
    }

    @Test
    @DisplayName("Should bypass public endpoints without token")
    void shouldBypassPublicEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainExecuted.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainExecuted.get(), "Chain should be executed for public endpoints");
    }

    @Test
    @DisplayName("Should reject protected endpoints when Authorization header is missing")
    void shouldRejectProtectedEndpointsMissingAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders/101").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Should reject protected endpoints when token signature is invalid")
    void shouldRejectInvalidTokenSignature() throws Exception {
        // Token di-sign dengan keypair lain
        KeyPair otherKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String forgedToken = Jwts.builder()
                .subject("attacker@test.com")
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders/101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Should allow valid token and inject user headers into downstream request")
    void shouldAllowValidTokenAndInjectHeaders() {
        String validToken = Jwts.builder()
                .subject("customer@example.com")
                .claim("role", "ROLE_CUSTOMER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders/101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainExecuted.set(true);
            // Verifikasi header downstream telah di-inject
            ServerWebExchange mutated = (ServerWebExchange) ex;
            assertEquals("customer@example.com", mutated.getRequest().getHeaders().getFirst("X-User-Email"));
            assertEquals("ROLE_CUSTOMER", mutated.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainExecuted.get(), "Chain should be executed for valid token");
    }
}
