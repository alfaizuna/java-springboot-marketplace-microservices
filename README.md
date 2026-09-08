# 🛒 Java Spring Boot Marketplace Microservices

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.3-blue.svg)](https://spring.io/projects/spring-cloud)
[![Spring Cloud Gateway](https://img.shields.io/badge/Gateway-WebFlux-blueviolet.svg)](https://spring.io/projects/spring-cloud-gateway)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-blueviolet.svg)](https://flywaydb.org/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

A **production-ready Marketplace Microservices** system built with Java 21 and Spring Boot 4. Demonstrates clean microservice decomposition, asymmetric RS256 JWT authentication, HMAC payment webhook security, and service-to-service communication — all orchestrated via Docker Compose.

> 🇮🇩 *Dokumentasi Bahasa Indonesia: [README.id.md](README.id.md)*

---

## 🏗️ Architecture Overview

```
                        ┌─────────────────────────────────┐
                        │        Client / Postman          │
                        └─────────────┬───────────────────┘
                                      │ HTTP :8080
                                      ▼
                        ┌─────────────────────────────────┐
                        │         API Gateway (8080)       │
                        │   Spring Cloud Gateway WebFlux   │
                        │   • RS256 JWT Validation         │
                        │   • Route Forwarding             │
                        │   • X-User-Email / X-User-Role   │
                        └──┬──────┬──────┬──────┬─────────┘
                           │      │      │      │
              /auth,/users │      │/products    │/webhooks
                           │      │      │      │
              ┌────────────▼─┐  ┌─▼──────────┐  ┌▼───────────────────┐
              │ auth-service │  │product-svc │  │payment-webhook-svc │
              │    :8081     │  │   :8082    │  │       :8084        │
              │  auth_db     │  │ product_db │  │    payment_db      │
              │  Redis       │  └────────────┘  │    Redis           │
              └──────────────┘        ▲         └────────────────────┘
                                      │ check-stock
                                /orders│
                        ┌─────────────▼──────┐
                        │   order-service    │
                        │       :8083        │
                        │     order_db       │
                        └────────────────────┘
```

---

## 🧩 Services

| Service | Port | Database | Responsibilities |
|---------|------|----------|-----------------|
| **api-gateway** | `8080` | — | Single entry point, JWT RS256 validation, route forwarding |
| **auth-service** | `8081` | `auth_db` | Register, Login, Refresh Token, Logout, RS256 JWT |
| **product-service** | `8082` | `product_db` | Product catalog, stock check (internal), ADMIN-only create |
| **order-service** | `8083` | `order_db` | Order creation with stock verification, state machine |
| **payment-webhook-service** | `8084` | `payment_db` | HMAC-SHA256 webhook, Redis idempotency, audit trail |

---

## 🔐 Security Design

### Asymmetric RS256 JWT
- **auth-service** → holds `private_key.pem`, signs tokens
- **api-gateway** → holds only `public_key.pem`, validates tokens
- **Other services** → trust `X-User-Email` & `X-User-Role` headers injected by gateway

### Payment Webhook Security
| Threat | Solution |
|--------|----------|
| Payload spoofing | HMAC-SHA256 signature verification |
| Timing attacks | Constant-time comparison (`MessageDigest.isEqual`) |
| Duplicate processing | Redis `SETNX` atomic idempotency lock |
| Amount tampering | Strict amount integrity check against order DB |

---

## 📡 API Endpoints

All endpoints are accessed via the **API Gateway at `:8080`**.

### Auth (`/api/v1/auth`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | Public | Register new user |
| `POST` | `/api/v1/auth/login` | Public | Login, get JWT + refresh token |
| `POST` | `/api/v1/auth/refresh` | Public | Refresh access token |
| `POST` | `/api/v1/auth/logout` | JWT | Logout, blacklist token |

### Products (`/api/v1/products`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/products` | Public | Browse product catalog (paginated) |
| `GET` | `/api/v1/products/{id}` | Public | Product detail |
| `POST` | `/api/v1/products` | JWT + ADMIN | Create new product |
| `GET` | `/api/v1/products/{sku}/check-stock?quantity=N` | JWT | Check stock availability |

### Orders (`/api/v1/orders`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/orders` | JWT | Create order (auto stock check) |
| `GET` | `/api/v1/orders/{orderNumber}/status` | JWT | Get order status |
| `POST` | `/api/v1/orders/{orderNumber}/simulate-webhook` | JWT | Generate test webhook payload |

### Payment Webhook (`/api/v1/webhooks`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/webhooks/payment` | HMAC | Receive payment callback |

---

## 🚀 Quickstart with Docker Compose

### Prerequisites
- **Docker** & **Docker Compose** v2+
- **Java 21** (for local development only)

### 1. Clone & Configure
```bash
git clone <repo-url>
cd java-springboot-marketplace-microservices

# Copy environment config
cp .env.example .env
# Edit .env sesuai kebutuhan (minimal ubah DB_PASSWORD)
```

### 2. Run All Services
```bash
docker compose up --build
```

> ☕ First build ~3-5 minutes (Maven downloads dependencies). Subsequent builds are much faster due to Docker layer caching.

### 3. Verify All Services Running
```bash
docker compose ps
```
Expected output:
```
NAME                          STATUS
marketplace-api-gateway       Up (healthy)
marketplace-auth-service      Up (healthy)
marketplace-product-service   Up (healthy)
marketplace-order-service     Up (healthy)
marketplace-payment-service   Up (healthy)
marketplace-postgres          Up (healthy)
marketplace-redis             Up (healthy)
```

---

## 🧪 End-to-End Simulation Flow

### 1. Register & Login
```bash
# Register
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "Password123!", "fullName": "Test User"}'

# Login — save the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "Password123!"}' \
  | jq -r '.accessToken')
```

### 2. Browse Products (Public)
```bash
curl -s http://localhost:8080/api/v1/products | jq
```

### 3. Create an Order (with automatic stock check)
```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderNumber": "ORD-DEMO-001",
    "productSku": "SKU-LAPTOP-001",
    "quantity": 2,
    "amount": 50000000.00
  }' | jq
```

### 4. Simulate Payment Webhook
```bash
# Generate valid HMAC signature + payload
WEBHOOK=$(curl -s -X POST http://localhost:8080/api/v1/orders/ORD-DEMO-001/simulate-webhook \
  -H "Authorization: Bearer $TOKEN")

SIGNATURE=$(echo $WEBHOOK | jq -r '.valid_signature')
PAYLOAD=$(echo $WEBHOOK | jq -r '.raw_payload')

# Send webhook to payment service
curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" \
  -H "X-Signature: $SIGNATURE" \
  -d "$PAYLOAD"
# Result: HTTP 200 → Order status becomes PAID!
```

### 5. Test Security Scenarios
```bash
# Test: Insufficient stock (HTTP 422)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"orderNumber": "ORD-DEMO-002", "productSku": "SKU-LAPTOP-001", "quantity": 9999, "amount": 1.00}'

# Test: Invalid HMAC signature (HTTP 401)
curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" \
  -H "X-Signature: invalid_fake_signature" \
  -d '{"transaction_id":"TRX-FAKE","order_number":"ORD-DEMO-001","gross_amount":250000.00,"transaction_status":"settlement"}'

# Test: Duplicate webhook (HTTP 200 idempotent)
# (Send the same valid webhook twice — second returns "Duplicate notification ignored")
```

---

## 🛠️ Local Development (Without Docker)

### Start Infrastructure Only
```bash
docker compose up -d postgres redis
```

### Run Each Service Individually
```bash
# In separate terminals:
cd auth-service    && ../mvnw spring-boot:run
cd product-service && ../mvnw spring-boot:run
cd order-service   && ../mvnw spring-boot:run
cd payment-webhook-service && ../mvnw spring-boot:run
cd api-gateway     && ../mvnw spring-boot:run
```

### Run Unit Tests
```bash
# All unit tests (excludes @Tag("integration") tests)
./mvnw test

# Integration tests only (requires running Docker infra)
./mvnw test -Dgroups=integration
```

---

## 📁 Project Structure

```
marketplace-microservices/
├── pom.xml                          ← Root Multi-Module POM
├── docker-compose.yml               ← Full microservices orchestration
├── docker/
│   └── init-dbs.sh                  ← Creates auth_db, product_db, order_db, payment_db
│
├── api-gateway/                     ← Port 8080 — Spring Cloud Gateway
│   ├── Dockerfile
│   └── src/main/java/.../gateway/
│       ├── config/RsaKeyConfig.java
│       └── filter/JwtAuthenticationFilter.java
│
├── auth-service/                    ← Port 8081 — JWT RS256 Auth
│   ├── Dockerfile
│   └── src/main/resources/certs/    ← private_key.pem + public_key.pem
│
├── product-service/                 ← Port 8082 — Product Catalog
│   └── Dockerfile
│
├── order-service/                   ← Port 8083 — Order + Stock Check
│   └── Dockerfile
│
└── payment-webhook-service/         ← Port 8084 — HMAC + Redis Idempotency
    └── Dockerfile
```

---

## 📖 Interactive API Documentation

Each service exposes Swagger UI when running locally:

| Service | Swagger URL |
|---------|------------|
| auth-service | http://localhost:8081/swagger-ui.html |
| product-service | http://localhost:8082/swagger-ui.html |
| order-service | http://localhost:8083/swagger-ui.html |
| payment-webhook-service | http://localhost:8084/swagger-ui.html |
