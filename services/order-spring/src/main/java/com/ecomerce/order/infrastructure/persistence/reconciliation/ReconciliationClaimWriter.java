package com.ecomerce.order.infrastructure.persistence.reconciliation;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserción de la marca, en un bean aparte y con transacción propia.
 *
 * <p>Las dos cosas son la misma vista dos veces: el proxy de Spring. Un
 * {@code REQUIRES_NEW} invocado desde otro método de la MISMA clase no pasa por el proxy y
 * no se aplica; y la violación de clave —que aquí es el resultado ESPERADO de una carrera—
 * deja la transacción marcada rollback-only, así que capturarla dentro acabaría en
 * {@code UnexpectedRollbackException} al commitear. Aquí se LANZA, y quien la interpreta
 * está fuera con su transacción ya revertida.
 */
@Component
class ReconciliationClaimWriter {

    private final ReconciliationClaimJpaRepository repository;

    ReconciliationClaimWriter(ReconciliationClaimJpaRepository repository) {
        this.repository = repository;
    }

    /** Inserta o lanza. {@code saveAndFlush} porque JPA diferiría el INSERT hasta el commit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(ReconciliationClaimJpa.ReconciliationClaimId key, Instant now) {
        repository.saveAndFlush(new ReconciliationClaimJpa(key, now));
    }
}
