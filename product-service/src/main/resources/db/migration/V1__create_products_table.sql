CREATE TABLE products
(
    id          BIGSERIAL PRIMARY KEY,
    sku         VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(19, 2) NOT NULL CHECK (price >= 0),
    stock       INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for faster SKU lookup
CREATE INDEX idx_products_sku ON products (sku);

-- Seed beberapa data awal untuk demo
INSERT INTO products (sku, name, description, price, stock)
VALUES ('SKU-LAPTOP-001', 'Laptop Gaming Ultra', 'High-performance gaming laptop with RTX 4080', 25000000.00, 50),
       ('SKU-MOUSE-001', 'Wireless Mouse Pro', 'Ergonomic wireless mouse with 3-year battery life', 350000.00, 200),
       ('SKU-KEYBOARD-001', 'Mechanical Keyboard RGB', 'Full-size mechanical keyboard with Cherry MX Blue switches', 850000.00, 150),
       ('SKU-MONITOR-001', '4K Gaming Monitor 27"', '27-inch 4K IPS panel with 144Hz refresh rate', 6500000.00, 30),
       ('SKU-HEADSET-001', 'Gaming Headset 7.1', 'Surround sound gaming headset with noise-cancelling microphone', 750000.00, 100);
