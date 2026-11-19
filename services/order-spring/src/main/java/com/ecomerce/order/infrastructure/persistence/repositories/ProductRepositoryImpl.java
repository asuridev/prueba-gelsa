package com.ecomerce.order.infrastructure.persistence.repositories;

import com.ecomerce.order.domain.aggregate.Product;
import com.ecomerce.order.domain.repository.ProductRepository;
import com.ecomerce.order.infrastructure.persistence.entities.ProductJpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto ProductRepository.
 *
 * <p>El {@code @Transactional(readOnly = true)} de clase hace que este adaptador se baste solo.
 * Casi siempre es un no-op: los caminos normales (REST, listeners) llegan aquí dentro de la
 * transacción que abrió el {@code UseCaseMediator}, y la propagación por defecto se une a ella
 * ignorando el flag. Importa en el único camino que NO trae transacción: el barrido de
 * reconciliación, que se despacha con {@code dispatchWithoutTransaction} justo para poder colocar
 * sus commits. Sin esto, una consulta que devuelva un agregado con colecciones LAZY reventaría
 * ahí con {@code LazyInitializationException}.
 *
 * <p>Por eso mismo, <b>toda escritura lleva su {@code @Transactional} de método</b>: sin él
 * heredaría el {@code readOnly} de la clase. Si añades una consulta de escritura a este
 * adaptador, anótala — el fallo es en caliente y no silencioso, que es lo que se busca.
 */
@Component
@Transactional(readOnly = true)
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return productJpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * Guarda la copia local en su PROPIA transacción.
     *
     * <p>{@code REQUIRES_NEW} no es decoración: a este método se llega también desde la
     * hidratación de la réplica ({@code onMiss}), que corre dentro de una consulta — y esa
     * transacción es {@code readOnly = true}. Unirse a ella con la propagación por defecto
     * sería escribir en una transacción física de solo lectura. Suspenderla y abrir una
     * propia es lo único que hace la escritura posible ahí.
     *
     * <p>Contrapartida deliberada: la copia queda commiteada aunque la lectura que la
     * provocó falle después. Es lo que se quiere — la réplica es un dato cacheado de otro
     * servicio, no parte del resultado de esta consulta.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Product save(Product entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        ProductJpa jpa = entity.getId() != null
                ? productJpaRepository.findById(entity.getId()).orElseGet(ProductJpa::new)
                : new ProductJpa();
        applyToJpa(entity, jpa);
        return toDomain(productJpaRepository.save(jpa));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        productJpaRepository.deleteById(id);
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private Product toDomain(ProductJpa jpa) {
        return new Product(
                jpa.getId(),
                jpa.getSku(),
                jpa.getName(),
                jpa.getPrice(),
                jpa.getCurrency(),
                jpa.getRetired(),
                jpa.getSourceVersion(),
                jpa.getLockVersion());
    }

    private void applyToJpa(Product domain, ProductJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setSku(domain.getSku());
        jpa.setName(domain.getName());
        jpa.setPrice(domain.getPrice());
        jpa.setCurrency(domain.getCurrency());
        jpa.setRetired(domain.getRetired());
        jpa.setSourceVersion(domain.getSourceVersion());
        jpa.setLockVersion(domain.getLockVersion());
    }
}
