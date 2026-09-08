package com.alfaizunawebid.order.client;

import com.alfaizunawebid.order.dto.StockCheckResponse;
import com.alfaizunawebid.order.exception.ProductServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP client untuk berkomunikasi dengan product-service.
 * Menggunakan Spring 6 RestClient (synchronous, blocking).
 * <p>
 * Tidak perlu JWT — akses melalui internal network (tidak lewat gateway).
 * </p>
 */
@Slf4j
@Component
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(
            RestClient.Builder builder,
            @Value("${services.product-service.url:http://localhost:8082}") String productServiceUrl) {
        this.restClient = builder
                .baseUrl(productServiceUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Cek ketersediaan stok untuk SKU tertentu.
     *
     * @param sku      Product SKU
     * @param quantity Jumlah unit yang dibutuhkan
     * @return StockCheckResponse berisi info sufficient/insufficient
     * @throws ProductServiceException jika product-service tidak bisa dihubungi atau SKU tidak ditemukan
     */
    public StockCheckResponse checkStock(String sku, int quantity) {
        log.info("Checking stock for SKU={}, quantity={} at product-service", sku, quantity);
        try {
            StockCheckResponse response = restClient.get()
                    .uri("/api/v1/products/{sku}/check-stock?quantity={quantity}", sku, quantity)
                    .retrieve()
                    .body(StockCheckResponse.class);

            log.info("Stock check result for SKU={}: sufficient={}, available={}",
                    sku, response != null ? response.isSufficient() : "null",
                    response != null ? response.getAvailableStock() : "null");

            return response;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Product not found at product-service: SKU={}", sku);
            throw new ProductServiceException("Product with SKU '" + sku + "' not found in catalog");
        } catch (ResourceAccessException e) {
            log.error("Cannot connect to product-service: {}", e.getMessage());
            throw new ProductServiceException("Product service is currently unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error calling product-service for SKU={}: {}", sku, e.getMessage());
            throw new ProductServiceException("Failed to verify product stock: " + e.getMessage());
        }
    }
}
