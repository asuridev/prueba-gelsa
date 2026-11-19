package com.ecomerce.order.infrastructure.persistence.reconciliation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionSystemException;

/**
 * Quién se lleva cada candidato del barrido de reconciliación.
 *
 * <p>Dos caminos y ninguno sobra, porque la fila puede no existir todavía:
 *
 * <ol>
 *   <li>Si existe y su marca caducó, un UPDATE condicional la renueva: 1 fila afectada = es
 *       mía.</li>
 *   <li>Si no existe, se INSERTA y la clave primaria arbitra la carrera entre dos réplicas
 *       que la insertan a la vez: la que pierde recibe la violación de clave, que aquí no es
 *       un error sino el desenlace normal.</li>
 * </ol>
 *
 * <p>Y si existe con la marca VIVA, el UPDATE no toca nada y el INSERT choca: las dos vías
 * dicen que no, que es lo que se quiere.
 */
@Component
public class ReconciliationClaimStore {

    private final ReconciliationClaimJpaRepository repository;
    private final ReconciliationClaimWriter writer;

    ReconciliationClaimStore(ReconciliationClaimJpaRepository repository, ReconciliationClaimWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    /**
     * @param activation qué encargo se reclama (una entidad puede esperar varios)
     * @param entityId cuál de ellos
     * @param now marca que se estampa
     * @param expiredBefore instante por debajo del cual un reclamo ajeno ya caducó
     * @return true si el candidato es de esta instancia
     */
    public boolean claim(String activation, UUID entityId, Instant now, Instant expiredBefore) {
        if (repository.claimIfExpired(activation, entityId, now, expiredBefore) == 1) {
            return true;
        }
        try {
            writer.insert(new ReconciliationClaimJpa.ReconciliationClaimId(activation, entityId), now);
            return true;
        } catch (DataIntegrityViolationException
                | PessimisticLockingFailureException
                | TransactionSystemException race) {
            // Otra réplica insertó la marca entre el UPDATE y el INSERT: suya es.
            //
            // Las DOS familias, y la segunda no es defensiva. Dos INSERT concurrentes con la
            // misma clave no siempre acaban en violación de restricción: InnoDB hace esperar
            // al segundo sobre el lock del primero, y si el desenlace tarda sale por
            // lock-wait timeout o deadlock — que Spring traduce a PessimisticLockingFailure,
            // no a DataIntegrityViolation. Capturando solo la primera, el barrido revienta
            // con una excepción en vez de ceder el candidato, y lo hace justo cuando hay
            // competencia, que es cuando este código existe para funcionar.
            //
            // Y la TERCERA, que llega por otro camino: el insert corre en su propia
            // transacción (REQUIRES_NEW), y esa transacción CONFIRMA al volver del proxy —
            // o sea, aquí dentro. Un fallo en ese commit no viaja como excepción de acceso a
            // datos sino como TransactionSystemException, que no es DataAccessException y se
            // escaparía de las dos anteriores. Significa lo mismo: la marca no quedó
            // confirmada, luego el candidato no es de esta instancia.
            //
            // Ceder de más es benigno: si el timeout viniera de otra cosa, esta pasada no se
            // lleva el candidato y la siguiente lo recoge. Lo caro es lo contrario.
            return false;
        }
    }
}
