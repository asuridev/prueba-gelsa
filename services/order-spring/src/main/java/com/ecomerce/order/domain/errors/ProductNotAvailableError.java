package com.ecomerce.order.domain.errors;

/**
 * Algún productId no está en la copia local del catálogo o está retirado.
 */
public class ProductNotAvailableError extends BusinessException {

    public ProductNotAvailableError(String message) {
        super(message, "PRODUCT_NOT_AVAILABLE", 422, null);
    }
}
