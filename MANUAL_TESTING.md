# Panduan Pengujian Manual API (Manual Testing Guide)

Dokumen ini berisi panduan langkah demi langkah untuk melakukan pengujian manual pada REST API **Java Spring Boot Marketplace Microservices** (Spring Boot 4, JWT RS256 Asymmetric, Redis Blacklist, Refresh Token Rotation, Payment Webhook State Machine & Redis Idempotency, serta Flyway Database Migration).

---

## 🛠️ Prasyarat Sebelum Pengujian

Pastikan service pendukung telah aktif:
1. **PostgreSQL**: `marketplace_db` aktif di port `5432`
2. **Redis**: Berjalan di port `6379` (`brew services start redis` atau `redis-server`)
3. **Aplikasi Spring Boot**: Berjalan di port `8080` (`./mvnw spring-boot:run`)

Variabel dasar yang digunakan dalam panduan ini:
- **Base URL**: `http://localhost:8080`
- **Email Uji**: `tester@example.com`
- **Password Awal**: `Password123!`
- **Password Baru**: `NewPassword456!`
- **Webhook Secret Key**: `super-secret-webhook-key-change-in-prod` (default di `application.yaml`)

---

## 📋 Peta Alur Pengujian Manual

```
[BAGIAN 1: AUTHENTICATION & USER MANAGEMENT]
[1. Public Key] ──► [2. Register] ──► [3. Cek RS256] ──► [4. Get Profile] ──► [5. Update Profile]
       │
       ▼
[6. Ganti Password] ──► [7. Login Baru] ──► [8. Refresh Token] ──► [9. Logout]
       │
       ▼
[10. Cek Redis Blacklist (403)] ──► [11. Cek Revoked Refresh (400)]

[BAGIAN 2: MARKETPLACE ORDERS & PAYMENT WEBHOOK]
[12. Buat Order (PENDING)] ──► [13. Cek Status Order] ──► [14. Simulator Webhook & HMAC]
       │
       ▼
[15. Kirim Webhook Sah (PAID)] ──► [16. Tes Idempotency Duplikat] ──► [17. Tes Spoofing & Fraud]

[BAGIAN 3: PORTAL DOKUMENTASI & AUDIT TRAIL]
[18. Swagger UI & OpenAPI] ──► [19. Audit DB & Redis CLI]
```

---

# Bagian 1: Autentikasi & Manajemen Pengguna

### Step 1: Mengambil RSA Public Key (Public Endpoint)
Mengambil kunci publik RSA format PEM untuk verifikasi signature JWT di sisi client atau resource server.

- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/auth/public-key`
- **Headers**: *(Tidak ada)*
- **cURL Command**:
  ```bash
  curl -i -X GET http://localhost:8080/api/v1/auth/public-key
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----"
  }
  ```

---

### Step 2: Registrasi User Baru
Mendaftarkan akun baru ke PostgreSQL. Sistem otomatis mem-hash password dengan BCrypt dan mengembalikan pasangan Access Token (RS256) serta Refresh Token.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/register`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "name": "Tester Manual",
    "email": "tester@example.com",
    "password": "Password123!"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -d '{
      "name": "Tester Manual",
      "email": "tester@example.com",
      "password": "Password123!"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0ZXJAZXhhbXBsZS5jb20i...",
    "refreshToken": "4a73752e-bb91-4cf1-8c46-953ebbb57fcf"
  }
  ```
> 💡 **Catatan**: Simpan nilai `token` (Access Token) dan `refreshToken` untuk langkah berikutnya!

---

### Step 3: Verifikasi Algoritma Token (RS256)
Memeriksa bagian Header dari Access Token yang dihasilkan pada Step 2 untuk memastikan algoritma bertipe Asymmetric RSA.

- **cURL / Bash Command**:
  ```bash
  ACCESS_TOKEN="<MASUKKAN_ACCESS_TOKEN>"
  echo "$ACCESS_TOKEN" | cut -d '.' -f 1 | base64 --decode
  ```
- **Ekspektasi Output**:
  ```json
  {"alg":"RS256"}
  ```

---

### Step 4: Akses Profile Sendiri (Protected Endpoint)
Memanggil endpoint profil user yang dilindungi oleh JWT Security Filter & Redis Blacklist Validator.

- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/users/me`
- **Headers**: 
  - `Authorization: Bearer <ACCESS_TOKEN>`
- **cURL Command**:
  ```bash
  curl -i -X GET http://localhost:8080/api/v1/users/me \
    -H "Authorization: Bearer <ACCESS_TOKEN>"
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "id": 1,
    "name": "Tester Manual",
    "email": "tester@example.com",
    "role": "USER"
  }
  ```

---

### Step 5: Update Profil User
Memperbarui nama pengguna yang sedang login.

- **Method**: `PUT`
- **URL**: `http://localhost:8080/api/v1/users/me`
- **Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "name": "Tester Manual Updated"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X PUT http://localhost:8080/api/v1/users/me \
    -H "Authorization: Bearer <ACCESS_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{
      "name": "Tester Manual Updated"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "id": 1,
    "name": "Tester Manual Updated",
    "email": "tester@example.com",
    "role": "USER"
  }
  ```

---

### Step 6: Ganti Password
Mengubah password user saat ini. Endpoint ini memverifikasi kesesuaian password lama (`currentPassword`) sebelum menyimpan hash BCrypt yang baru.

- **Method**: `PATCH`
- **URL**: `http://localhost:8080/api/v1/users/me/password`
- **Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "currentPassword": "Password123!",
    "newPassword": "NewPassword456!",
    "confirmationPassword": "NewPassword456!"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X PATCH http://localhost:8080/api/v1/users/me/password \
    -H "Authorization: Bearer <ACCESS_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{
      "currentPassword": "Password123!",
      "newPassword": "NewPassword456!",
      "confirmationPassword": "NewPassword456!"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "message": "Password changed successfully"
  }
  ```

---

### Step 7: Login dengan Password Baru
Melakukan autentikasi menggunakan password baru untuk memverifikasi bahwa perubahan password berhasil.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/login`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "email": "tester@example.com",
    "password": "NewPassword456!"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "email": "tester@example.com",
      "password": "NewPassword456!"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0ZXJAZXhhbXBsZS5jb20i...",
    "refreshToken": "8b919283-9ef2-4bc0-9345-21d491ea944f"
  }
  ```
> 💡 **Catatan**: Gunakan pasangan token baru dari respons login ini untuk langkah selanjutnya!

---

### Step 8: Refresh Access Token (Token Rotation)
Menghasilkan Access Token baru menggunakan Refresh Token yang aktif tanpa perlu login ulang.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/refresh-token`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/refresh-token \
    -H "Content-Type: application/json" \
    -d '{
      "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0ZXJAZXhhbXBsZS5jb20i..."
  }
  ```

---

### Step 9: Logout User (Redis Blacklist & Refresh Token Revoke)
Melakukan proses logout ganda:
1. **Access Token** dimasukkan ke Redis dengan key `blacklist:token:<token>` dan TTL sisa waktu kadaluarsa.
2. **Refresh Token** di database di-set `revoked = true`.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/logout`
- **Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/logout \
    -H "Authorization: Bearer <ACCESS_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{
      "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "message": "Logged out successfully"
  }
  ```

---

### Step 10: Pengujian Keamanan — Akses dengan Token yang Di-Blacklist (Harus 403)
Mencoba memanggil kembali `/users/me` dengan Access Token yang baru saja di-logout. Filter keamanan harus memeriksa Redis (kecepatan $O(1)$) dan menolaknya.

- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/users/me`
- **Headers**:
  - `Authorization: Bearer <LOGGED_OUT_ACCESS_TOKEN>`
- **cURL Command**:
  ```bash
  curl -i -X GET http://localhost:8080/api/v1/users/me \
    -H "Authorization: Bearer <LOGGED_OUT_ACCESS_TOKEN>"
  ```
- **Ekspektasi HTTP Status**: `403 Forbidden`
- **Alasan**: Token terdaftar di Redis Blacklist (`isTokenBlacklisted == true`).

---

### Step 11: Pengujian Keamanan — Refresh dengan Token yang Telah Di-Revoke (Harus 400)
Mencoba meminta Access Token baru menggunakan Refresh Token yang sudah di-revoke pada saat logout.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/refresh-token`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "refreshToken": "<REVOKED_REFRESH_TOKEN>"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/refresh-token \
    -H "Content-Type: application/json" \
    -d '{
      "refreshToken": "<REVOKED_REFRESH_TOKEN>"
    }'
  ```
- **Ekspektasi HTTP Status**: `400 Bad Request`
- **Alasan**: Di PostgreSQL, kolom `revoked = true`, sehingga `verifyExpiration()` melempar `IllegalArgumentException`.

---

# Bagian 2: Marketplace Orders & Payment Webhook Handler

### Step 12: Membuat Pesanan Baru (Create Order)
Membuat order baru di sistem marketplace dengan status awal `PENDING`.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/orders`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "orderNumber": "ORD-TEST-001",
    "amount": 150000.00
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/orders \
    -H "Content-Type: application/json" \
    -d '{
      "orderNumber": "ORD-TEST-001",
      "amount": 150000.00
    }'
  ```
- **Ekspektasi HTTP Status**: `201 Created`
- **Contoh Response**:
  ```json
  {
    "id": 1,
    "orderNumber": "ORD-TEST-001",
    "amount": 150000.00,
    "status": "PENDING"
  }
  ```

---

### Step 13: Cek Status Pesanan (Get Order Status)
Memeriksa status terkini pesanan di database sebelum diproses oleh payment gateway.

- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/orders/ORD-TEST-001/status`
- **Headers**: *(Tidak ada)*
- **cURL Command**:
  ```bash
  curl -i -X GET http://localhost:8080/api/v1/orders/ORD-TEST-001/status
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "orderNumber": "ORD-TEST-001",
    "amount": 150000.00,
    "status": "PENDING",
    "updatedAt": "2026-09-07T10:00:00"
  }
  ```

---

### Step 14: Simulator Webhook — Generate Payload & HMAC-SHA256 Signature
Endpoint helper simulator ini meniru payment gateway (Midtrans/Xendit/Stripe): menghasilkan mock payload berstatus `settlement` dan menghitung signature HMAC-SHA256 yang sah menggunakan secret key server.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/orders/ORD-TEST-001/simulate-webhook`
- **Headers**: *(Tidak ada)*
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/orders/ORD-TEST-001/simulate-webhook
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "description": "Use the values below to test POST /api/v1/webhooks/payment",
    "header_name": "X-Signature",
    "valid_signature": "a8f9c3b879f823e2a0e417865c3bf79d34e622b311394cba89736c56db32ec84",
    "raw_payload": "{\"payment_type\":\"qris\",\"transaction_status\":\"settlement\",\"order_number\":\"ORD-TEST-001\",\"gross_amount\":150000.00,\"transaction_id\":\"TRX-8a4f21bc\"}"
  }
  ```
> 💡 **Penting**: Simpan nilai `valid_signature` dan `raw_payload` persis apa adanya (termasuk urutan string JSON) untuk Step 15 & 16!

---

### Step 15: Mengirim Webhook Pembayaran Sah (Valid Payment Webhook)
Mengirim webhook callback yang sah ke listener aplikasi. Sistem akan memverifikasi signature secara *constant-time*, mengunci Redis idempotency lock, memperbarui status order menjadi `PAID`, dan mencatat audit log transaksi.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/webhooks/payment`
- **Headers**:
  - `Content-Type: application/json`
  - `X-Signature: <VALID_SIGNATURE>`
- **Request Body**: *(Gunakan nilai raw_payload dari Step 14)*
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
    -H "Content-Type: application/json" \
    -H "X-Signature: a8f9c3b879f823e2a0e417865c3bf79d34e622b311394cba89736c56db32ec84" \
    -d '{"payment_type":"qris","transaction_status":"settlement","order_number":"ORD-TEST-001","gross_amount":150000.00,"transaction_id":"TRX-8a4f21bc"}'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "status": "success",
    "message": "Payment webhook processed successfully"
  }
  ```
- **Verifikasi Status Order**:
  Jalankan kembali Step 13 (`GET /api/v1/orders/ORD-TEST-001/status`). Status sekarang bernilai: `"status": "PAID"`.

---

### Step 16: Pengujian Idempotensi — Mengirim Webhook Duplikat (Network Retry)
Mensimulasikan perilaku Payment Gateway yang mengirim ulang notifikasi yang sama persis (network retry). Redis Idempotency Engine harus mendeteksi `transaction_id` yang sama dan langsung merespon `200 OK` tanpa memproses ulang pesanan.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/webhooks/payment`
- **Headers**:
  - `Content-Type: application/json`
  - `X-Signature: <VALID_SIGNATURE>`
- **Request Body**: *(Gunakan payload yang sama persis dengan Step 15)*
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
    -H "Content-Type: application/json" \
    -H "X-Signature: a8f9c3b879f823e2a0e417865c3bf79d34e622b311394cba89736c56db32ec84" \
    -d '{"payment_type":"qris","transaction_status":"settlement","order_number":"ORD-TEST-001","gross_amount":150000.00,"transaction_id":"TRX-8a4f21bc"}'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "status": "success",
    "message": "Duplicate notification ignored"
  }
  ```

---

### Step 17: Pengujian Keamanan Webhook (Negative Security Tests)

#### 17A. Spoofing Attack — Signature Palsu (Harus 401 Unauthorized)
Mencoba memalsukan notifikasi settlement tanpa signature yang sah:

```bash
curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" \
  -H "X-Signature: fake_malicious_signature_hash" \
  -d '{"payment_type":"qris","transaction_status":"settlement","order_number":"ORD-TEST-001","gross_amount":150000.00,"transaction_id":"TRX-HACKER"}'
```
- **Ekspektasi HTTP Status**: `401 Unauthorized`
- **Response**:
  ```json
  {
    "error": "Unauthorized",
    "message": "Invalid HMAC signature"
  }
  ```

#### 17B. Amount Tampering / Fraud Guard (Status Berubah Menjadi FAILED)
Mencoba membayar dengan nominal yang tidak cocok dengan data order di database (misal Rp 1.000 untuk pesanan Rp 150.000):

1. Buat order baru untuk uji fraud:
   ```bash
   curl -i -X POST http://localhost:8080/api/v1/orders \
     -H "Content-Type: application/json" \
     -d '{"orderNumber": "ORD-FRAUD-001", "amount": 150000.00}'
   ```
2. Dapatkan HMAC signature untuk payload yang dimanipulasi:
   ```bash
   # Hitung HMAC dari raw json menggunakan bash openssl:
   RAW_JSON='{"payment_type":"qris","transaction_status":"settlement","order_number":"ORD-FRAUD-001","gross_amount":1000.00,"transaction_id":"TRX-FRAUD-01"}'
   SIG=$(echo -n "$RAW_JSON" | openssl dgst -sha256 -hmac "super-secret-webhook-key-change-in-prod" | awk '{print $2}')
   
   curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
     -H "Content-Type: application/json" \
     -H "X-Signature: $SIG" \
     -d "$RAW_JSON"
   ```
3. Cek status order `ORD-FRAUD-001`:
   ```bash
   curl -i -X GET http://localhost:8080/api/v1/orders/ORD-FRAUD-001/status
   ```
- **Ekspektasi**: Status order otomatis berubah menjadi `"status": "FAILED"` karena terdeteksi fraud mismatch nominal.

---

# Bagian 3: Portal Dokumentasi & OpenAPI

### Step 18: Verifikasi OpenAPI Docs & Swagger UI Portal

1. **Akses OpenAPI 3 Spec (JSON)**:
   ```bash
   curl -i -X GET http://localhost:8080/v3/api-docs
   ```
   - **Ekspektasi**: `200 OK` berisi JSON OpenAPI 3 dengan judul `Java Spring Boot Marketplace Microservices API`.

2. **Akses Swagger UI**:
   Buka browser di:
   - [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (redirect ke `/swagger-ui/index.html`)
   - Klik tombol **Authorize 🔓** untuk memasukkan JWT Bearer Token atau lakukan tes interaktif seluruh endpoint.

---

# Bagian 4: Verifikasi Melalui Database & Redis CLI

### 1. Periksa Data di PostgreSQL (`marketplace_db`)
```bash
psql -U postgres -d marketplace_db
```

Query SQL yang berguna:
```sql
-- 1. Periksa daftar user terdaftar
SELECT id, name, email, role, created_at FROM _user;

-- 2. Periksa status refresh token dan flag revoked
SELECT id, token, user_id, expiry_date, revoked FROM refresh_tokens;

-- 3. Periksa daftar pesanan dan state transition (PENDING / PAID / FAILED)
SELECT id, order_number, amount, status, created_at, updated_at FROM orders;

-- 4. Periksa audit trail log pembayaran yang masuk melalui webhook
SELECT id, transaction_id, order_number, payment_type, gross_amount, transaction_status, created_at 
FROM payment_transaction_logs;

-- 5. Periksa riwayat migrasi database Flyway
SELECT installed_rank, version, description, success, installed_on FROM flyway_schema_history;
```

---

### 2. Periksa Cache & Lock di Redis CLI
```bash
redis-cli
```

Perintah Redis yang berguna:
```text
# 1. Periksa daftar token yang sedang di-blacklist
KEYS "blacklist:token:*"

# 2. Cek sisa TTL (dalam detik) dari Access Token yang di-blacklist
TTL "blacklist:token:<access_token>"

# 3. Periksa status idempotency key payment webhook
KEYS "payment:webhook:idempotency:*"

# 4. Lihat status transaksi di dalam idempotency lock (PROCESSING atau COMPLETED)
GET "payment:webhook:idempotency:<transaction_id>"

# 5. Cek sisa TTL idempotency lock (default 24 jam = 86400 detik)
TTL "payment:webhook:idempotency:<transaction_id>"
```
