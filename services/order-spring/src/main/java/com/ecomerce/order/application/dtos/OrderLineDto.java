package com.ecomerce.order.application.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineDto(
    UUID id,
    UUID orderId,
    UUID productId,
    String sku,
    String productName,
    BigDecimal unitPrice,
    Integer quantity
) {
}
