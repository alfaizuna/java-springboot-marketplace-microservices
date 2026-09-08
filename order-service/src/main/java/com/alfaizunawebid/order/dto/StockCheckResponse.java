package com.alfaizunawebid.order.dto;

import lombok.Data;

/**
 * DTO untuk menerima response dari product-service endpoint
 * GET /api/v1/products/{sku}/check-stock?quantity=N
 */
@Data
public class StockCheckResponse {

    private String sku;
    private Integer availableStock;
    private Integer requestedQuantity;
    private boolean sufficient;
    private String message;
}
