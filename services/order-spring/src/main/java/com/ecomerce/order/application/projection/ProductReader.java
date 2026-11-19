package com.ecomerce.order.application.projection;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.domain.aggregate.Product;
import com.ecomerce.order.domain.errors.ProductNotAvailableError;
import com.ecomerce.order.domain.repository.ProductRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura de la copia local de Product (propiedad de catalog), con la
 * política declarada para cuando el dato todavía no está: <strong>fail</strong>.
 *
 * <p>La copia es eventualmente consistente: la mantiene ProductProjector al ritmo de
 * los eventos de catalog. Nunca la expongas tal cual como recurso propio.
 *
 * <p>Tolerancia declarada en el diseño: Se cobra el precio que tenía la copia al crear el pedido, que es también el que se congela en la línea. Un cambio de precio en catalog tarda unos segundos en llegar y durante ese margen se cobra el anterior; el diseñador lo acepta.
 *
 * <p>Lo usan: createOrder.
 */
@ApplicationComponent
public class ProductReader {

    private final ProductRepository repository;

    public ProductReader(ProductRepository repository) {
        this.repository = repository;
    }

    /**
     * Devuelve la copia local, o falla con {@code PRODUCT_NOT_AVAILABLE} si catalog todavía
     * no nos ha informado de ese id. Sin el dato no se puede decidir bien, y así lo
     * declara el diseño.
     */
    public Product byKey(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ProductNotAvailableError("No se conoce Product " + id + " (dato de catalog aún no replicado)"));
    }
}
