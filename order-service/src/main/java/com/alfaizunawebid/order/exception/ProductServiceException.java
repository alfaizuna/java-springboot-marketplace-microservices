package com.alfaizunawebid.order.exception;

/**
 * Exception yang dilempar saat terjadi masalah komunikasi dengan product-service,
 * misalnya product tidak ditemukan atau service tidak tersedia.
 */
public class ProductServiceException extends RuntimeException {

    public ProductServiceException(String message) {
        super(message);
    }
}
