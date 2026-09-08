-- Migration V2: Add product_sku and quantity columns to orders table
-- Diperlukan untuk integrasi dengan product-service (stock check)

ALTER TABLE orders
    ADD COLUMN product_sku VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN quantity    INTEGER       NOT NULL DEFAULT 1;

-- Hapus DEFAULT setelah kolom ter-populate (kolom menjadi required)
ALTER TABLE orders
    ALTER COLUMN product_sku DROP DEFAULT,
    ALTER COLUMN quantity DROP DEFAULT;

-- Index untuk lookup berdasarkan SKU produk
CREATE INDEX idx_orders_product_sku ON orders (product_sku);
