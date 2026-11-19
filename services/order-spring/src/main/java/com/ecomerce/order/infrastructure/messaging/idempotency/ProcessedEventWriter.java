package com.ecomerce.order.infrastructure.messaging.idempotency;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escritura del registro de procesados: una transacción propia por operación, en un
 * bean aparte del guard.
 *
 * <p><b>Las dos cosas son necesarias y por el mismo motivo: el proxy.</b> Si el guard
 * llamara a sus propios métodos anotados, la llamada no pasaría por el proxy y
 * {@code REQUIRES_NEW} no se aplicaría — el registro moriría con el rollback del
 * handler, que es exactamente lo que no debe pasar. Y si el {@code catch} del
 * duplicado viviera dentro de esta transacción, capturarlo no salvaría nada: la
 * violación de clave la deja marcada rollback-only y el commit posterior fallaría.
 * Aquí se LANZA; quien la interpreta está fuera.
 *
 * <p>No es una clase para tocar: quien la pliegue dentro de {@link IdempotencyGuard}
 * reintroduce los dos fallos a la vez, y solo se manifiestan con dos entregas
 * simultáneas del mismo mensaje — es decir, con varias réplicas en marcha.
 */
@Component
class ProcessedEventWriter {

    private final ProcessedEventJpaRepository processedEventRepository;

    ProcessedEventWriter(ProcessedEventJpaRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    boolean exists(ProcessedEventJpa.ProcessedEventId key) {
        return processedEventRepository.existsById(key);
    }

    /** Inserta o lanza. La violación de clave es el resultado esperado de una carrera. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(ProcessedEventJpa.ProcessedEventId key) {
        processedEventRepository.saveAndFlush(new ProcessedEventJpa(key, Instant.now()));
    }
}
