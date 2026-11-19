package com.ecomerce.order.application.projection;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.domain.aggregate.Product;
import com.ecomerce.order.domain.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mantiene al día la copia local de Product, propiedad de catalog.
 *
 * <p>Esto es una <strong>proyección</strong>, no una fuente de verdad: solo se escribe
 * desde aquí, nunca desde un handler de negocio, y no se le aplican invariantes del
 * dominio propio — las suyas las garantiza catalog.
 *
 * <p>El upsert es idempotente por construcción, así que una reentrega del mismo evento
 * no corrompe la copia. La deduplicación estricta la hace antes el IdempotencyGuard
 * del listener; esto es la segunda red.
 *
 * <p>Necesidad que la justifica: catalog.productPricing — Precio, moneda, sku y nombre de cada producto pedido, para calcular el importe y congelarlo en la línea.
 */
@ApplicationComponent
public class ProductProjector {

    private final ProductRepository repository;

    public ProductProjector(ProductRepository repository) {
        this.repository = repository;
    }

    /**
     * Aplica el estado que informa catalog: actualiza la copia si ya existe,
     * la crea si es la primera noticia de este id.
     *
     * <p>Se ejecuta dentro de la transacción que abre el UseCaseMediator al despachar
     * la operación de proyección; no la abre por su cuenta.
     */
    public void apply(UUID id, String sku, String name, BigDecimal price, String currency, Boolean retired, Long sourceVersion) {
        repository.findById(id).ifPresentOrElse(existing -> {
            // El payload de la suscripción no trae ningún instante del hecho: el orden lo decide la
            // versión de catalog, que comprueba el handler de la operación antes de llamar aquí.
            existing.applySnapshot(sku, name, price, currency, retired, sourceVersion);
            repository.save(existing);
        }, () -> repository.save(Product.projectionOf(id, sku, name, price, currency, retired, sourceVersion)));
    }

}
