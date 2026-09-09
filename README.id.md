# 🛒 Java Spring Boot Marketplace Microservices

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.3-blue.svg)](https://spring.io/projects/spring-cloud)
[![Spring Cloud Gateway](https://img.shields.io/badge/Gateway-WebFlux-blueviolet.svg)](https://spring.io/projects/spring-cloud-gateway)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-blueviolet.svg)](https://flywaydb.org/)

Sistem backend **Marketplace Microservices** *production-ready* berbasis Java 21 dan Spring Boot 4. Mendemonstrasikan dekomposisi layanan yang bersih, autentikasi JWT RS256 asimetris, keamanan webhook pembayaran HMAC, dan komunikasi antar-layanan — semuanya diorkestrasikan melalui Docker Compose.

> 🇬🇧 *English documentation: [README.md](README.md)*

---

## 🏗️ Arsitektur Sistem

```
                        ┌─────────────────────────────────┐
                        │        Client / Postman          │
                        └─────────────┬───────────────────┘
                                      │ HTTP :8080
                                      ▼
                        ┌─────────────────────────────────┐
                        │         API Gateway (8080)       │
                        │   Spring Cloud Gateway WebFlux   │
                        │   • Validasi RS256 JWT           │
                        │   • Routing ke semua services    │
                        │   • Injeksi X-User-Email/Role    │
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
                                      │ cek stok
                                /orders│
                        ┌─────────────▼──────┐
                        │   order-service    │
                        │       :8083        │
                        │     order_db       │
                        └────────────────────┘
```

---

## 🧩 Daftar Services

| Service | Port | Database | Fungsi |
|---------|------|----------|--------|
| **api-gateway** | `8080` | — | Single entry point, validasi JWT RS256, routing |
| **auth-service** | `8081` | `auth_db` | Register, Login, Refresh Token, Logout, RS256 JWT |
| **product-service** | `8082` | `product_db` | Katalog produk, cek stok (internal), buat produk (ADMIN) |
| **order-service** | `8083` | `order_db` | Buat order dengan verifikasi stok, state machine |
| **payment-webhook-service** | `8084` | `payment_db` | Webhook HMAC-SHA256, idempotency Redis, audit trail |

---

## 🔐 Desain Keamanan

### JWT Asimetris RS256
- **auth-service** → menyimpan `private_key.pem`, menandatangani token
- **api-gateway** → hanya menyimpan `public_key.pem`, memvalidasi token
- **Service lain** → mempercayai header `X-User-Email` & `X-User-Role` yang diinjeksi gateway

### Keamanan Webhook Pembayaran
| Ancaman | Solusi |
|---------|--------|
| Pemalsuan payload | Verifikasi signature HMAC-SHA256 |
| Timing attack | Perbandingan waktu konstan (`MessageDigest.isEqual`) |
| Pemrosesan ganda | Kunci idempotency Redis atomic (`SETNX`) |
| Pemalsuan nominal | Validasi jumlah transaksi ke database order |

---

## 📡 Daftar Endpoint API

Semua endpoint diakses melalui **API Gateway di `:8080`**.

### Auth (`/api/v1/auth`)
| Method | Endpoint | Auth | Keterangan |
|--------|----------|------|------------|
| `POST` | `/api/v1/auth/register` | Public | Daftar user baru |
| `POST` | `/api/v1/auth/login` | Public | Login, dapatkan JWT + refresh token |
| `POST` | `/api/v1/auth/refresh` | Public | Perbarui access token |
| `POST` | `/api/v1/auth/logout` | JWT | Logout, blacklist token |

### Produk (`/api/v1/products`)
| Method | Endpoint | Auth | Keterangan |
|--------|----------|------|------------|
| `GET` | `/api/v1/products` | Public | Lihat katalog produk (paginasi) |
| `GET` | `/api/v1/products/{id}` | Public | Detail produk |
| `POST` | `/api/v1/products` | JWT + ADMIN | Tambah produk baru |
| `GET` | `/api/v1/products/{sku}/check-stock?quantity=N` | JWT | Cek ketersediaan stok |

### Order (`/api/v1/orders`)
| Method | Endpoint | Auth | Keterangan |
|--------|----------|------|------------|
| `POST` | `/api/v1/orders` | JWT | Buat order (cek stok otomatis) |
| `GET` | `/api/v1/orders/{orderNumber}/status` | JWT | Cek status order |
| `POST` | `/api/v1/orders/{orderNumber}/simulate-webhook` | JWT | Generate payload webhook untuk tes |

### Payment Webhook (`/api/v1/webhooks`)
| Method | Endpoint | Auth | Keterangan |
|--------|----------|------|------------|
| `POST` | `/api/v1/webhooks/payment` | HMAC | Terima callback pembayaran |

---

## 🚀 Cara Menjalankan dengan Docker Compose

### Prasyarat
- **Docker** & **Docker Compose** v2+
- **Java 21** (hanya untuk development lokal)

### 1. Clone & Konfigurasi
```bash
git clone <repo-url>
cd java-springboot-marketplace-microservices

# Salin file konfigurasi environment
cp .env.example .env
# Edit .env sesuai kebutuhan (minimal ganti DB_PASSWORD)
```

### 2. Jalankan Semua Services
```bash
docker compose up --build
```

> ☕ Build pertama ~3-5 menit (Maven mengunduh dependensi). Build berikutnya jauh lebih cepat berkat Docker layer caching.

### 3. Verifikasi Semua Services Berjalan
```bash
docker compose ps
```
Output yang diharapkan:
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

## 🧪 Alur Simulasi End-to-End

### 1. Register & Login
```bash
# Register
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "Password123!", "fullName": "Test User"}'

# Login — simpan token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "Password123!"}' \
  | jq -r '.accessToken')
```

### 2. Lihat Produk (Publik)
```bash
curl -s http://localhost:8080/api/v1/products | jq
```

### 3. Buat Order (dengan cek stok otomatis)
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

### 4. Simulasi Pembayaran via Webhook
```bash
# Generate payload + HMAC signature yang valid
WEBHOOK=$(curl -s -X POST http://localhost:8080/api/v1/orders/ORD-DEMO-001/simulate-webhook \
  -H "Authorization: Bearer $TOKEN")

SIGNATURE=$(echo $WEBHOOK | jq -r '.valid_signature')
PAYLOAD=$(echo $WEBHOOK | jq -r '.raw_payload')

# Kirim webhook ke payment service
curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" \
  -H "X-Signature: $SIGNATURE" \
  -d "$PAYLOAD"
# Hasil: HTTP 200 → Status order berubah jadi PAID!
```

### 5. Uji Skenario Keamanan
```bash
# Uji: Stok tidak mencukupi (HTTP 422)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"orderNumber":"ORD-FAIL-001","productSku":"SKU-LAPTOP-001","quantity":9999,"amount":1.00}'

# Uji: HMAC signature palsu (HTTP 401)
curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" \
  -H "X-Signature: ini_signature_palsu" \
  -d '{"transaction_id":"TRX-FAKE","order_number":"ORD-DEMO-001","gross_amount":250000.00,"transaction_status":"settlement"}'

# Uji: Idempotency — kirim webhook yang sama dua kali
# Hasil: HTTP 200 dengan pesan "Duplicate notification ignored"
```

---

## 🛠️ Development Lokal (Tanpa Docker)

### Jalankan Hanya Infrastruktur
```bash
docker compose up -d postgres redis
```

### Jalankan Setiap Service Secara Terpisah
```bash
# Di terminal terpisah untuk masing-masing:
cd auth-service    && ../mvnw spring-boot:run
cd product-service && ../mvnw spring-boot:run
cd order-service   && ../mvnw spring-boot:run
cd payment-webhook-service && ../mvnw spring-boot:run
cd api-gateway     && ../mvnw spring-boot:run
```

### Menjalankan Unit Test
```bash
# Semua unit test (mengecualikan @Tag("integration"))
./mvnw test

# Integration test saja (membutuhkan Docker berjalan)
./mvnw test -Dgroups=integration
```

---

## 📁 Struktur Project

```
marketplace-microservices/
├── pom.xml                          ← Root Multi-Module POM
├── docker-compose.yml               ← Orkestrasi full microservices
├── docker/
│   └── init-dbs.sh                  ← Membuat auth_db, product_db, order_db, payment_db
│
├── api-gateway/                     ← Port 8080 — Spring Cloud Gateway
├── auth-service/                    ← Port 8081 — JWT RS256 Auth
├── product-service/                 ← Port 8082 — Katalog Produk
├── order-service/                   ← Port 8083 — Order + Cek Stok
└── payment-webhook-service/         ← Port 8084 — HMAC + Redis Idempotency
```

---

## 👨‍💻 Relevansi Industri

Proyek ini mendemonstrasikan keahlian di bidang:
- **Microservices Architecture** — dekomposisi service yang bersih dengan Maven Multi-Module
- **API Gateway Pattern** — single entry point dengan Spring Cloud Gateway WebFlux
- **Keamanan JWT Asimetris** — RS256 dengan pemisahan private/public key antar service
- **Webhook Security** — HMAC-SHA256, timing-attack resistant, Redis idempotency
- **Service-to-Service Communication** — Spring 6 RestClient untuk komunikasi internal
- **Database-per-Service** — PostgreSQL multi-database dengan Flyway migration
- **Docker Orchestration** — multi-container dengan healthcheck dan dependency ordering
