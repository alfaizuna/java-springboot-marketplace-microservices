package com.alfaizunawebid.product.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockCheckResponse {

    private String sku;
    private Integer availableStock;
    private Integer requestedQuantity;
    private boolean sufficient;
    private String message;
}
