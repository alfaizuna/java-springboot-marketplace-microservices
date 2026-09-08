package com.alfaizunawebid.order.dto;

import com.alfaizunawebid.order.model.Order;
import com.alfaizunawebid.order.model.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private String productSku;
    private Integer quantity;
    private BigDecimal amount;
    private OrderStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .productSku(order.getProductSku())
                .quantity(order.getQuantity())
                .amount(order.getAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
