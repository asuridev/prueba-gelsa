package com.ecomerce.order.domain.entity;

import com.ecomerce.order.domain.errors.InvariantViolatedException;
import com.ecomerce.order.domain.identity.Uuids;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una línea del pedido; congela el producto y su precio en el momento de pedir.
 */
public class OrderLine {

    private UUID id = Uuids.v7();

    // Id del producto en catalog (referencia por valor a la réplica).
    private UUID productId;

    // SKU del producto al crear el pedido.
    private String sku;

    // Nombre del producto al crear el pedido.
    private String productName;

    // Precio unitario congelado al crear el pedido.
    private BigDecimal unitPrice;

    private Integer quantity;

    /**
     * Factory de creación: congela el producto y su precio en el momento de pedir. El precio de la
     * línea no cambia después (no hay mutadores); el resto de cotas las comprueba ensureInvariants.
     */
    public static OrderLine create(UUID productId, String sku, String productName, BigDecimal unitPrice, Integer quantity) {
        if (productId == null || sku == null || productName == null || unitPrice == null || quantity == null) {
            throw new InvariantViolatedException("OrderLine: productId, sku, productName, unitPrice y quantity son obligatorios.");
        }
        return new OrderLine(Uuids.v7(), productId, sku, productName, unitPrice, quantity);
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public OrderLine(UUID id, UUID productId, String sku, String productName, BigDecimal unitPrice, Integer quantity) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (sku != null && (sku.codePointCount(0, sku.length()) > 64)) {
            throw new InvariantViolatedException("OrderLine: sku queda fuera de sus cotas.");
        }
        if (productName != null && (productName.codePointCount(0, productName.length()) > 200)) {
            throw new InvariantViolatedException("OrderLine: productName queda fuera de sus cotas.");
        }
        if (unitPrice != null && (unitPrice.compareTo(new BigDecimal("0")) < 0)) {
            throw new InvariantViolatedException("OrderLine: unitPrice queda fuera de sus cotas.");
        }
        if (quantity != null && ((quantity < 1) || (quantity > 99))) {
            throw new InvariantViolatedException("OrderLine: quantity queda fuera de sus cotas.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
