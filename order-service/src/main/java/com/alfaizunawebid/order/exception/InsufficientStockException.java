package com.alfaizunawebid.order.exception;

/**
 * Exception yang dilempar saat stok produk tidak mencukupi untuk memenuhi order.
 * Menghasilkan HTTP 422 Unprocessable Entity.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku, int requested, int available) {
        super(String.format(
                "Insufficient stock for SKU '%s'. Requested: %d, Available: %d",
                sku, requested, available
        ));
    }
}
