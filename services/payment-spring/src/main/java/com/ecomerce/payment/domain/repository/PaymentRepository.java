package com.ecomerce.payment.domain.repository;

import com.ecomerce.payment.domain.aggregate.Payment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de persistencia del agregado Payment; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface PaymentRepository {

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByChargeRequestId(String chargeRequestId);

    Page<Payment> list(Pageable pageable);

    /**
     * Los filtros de listPaymentsByCustomer, como los declara el diseño (DSL 2.22). Un filtro a null no restringe.
     */
    record ListPaymentsByCustomerFilter(String customerRef) {
    }

    /** La búsqueda de listPaymentsByCustomer con los filtros del diseño y solo esos. */
    Page<Payment> listPaymentsByCustomer(ListPaymentsByCustomerFilter filter, Pageable pageable);

    Payment save(Payment entity);

    /**
     * Registra un cobro nuevo en su propia transacción, que confirma antes de volver.
     *
     * @return el cobro guardado, o vacío si otro cobro con la misma clave natural (chargeRequestId) ya existía:
     *         quien llama relee al ganador. Nunca deja un segundo cobro.
     */
    Optional<Payment> register(Payment entity);

    /**
     * Los cobros que esperan un desenlace (pending, actionRequired, capturing, canceling, refunding) con
     * awaitingSince anterior a {@code staleBefore}, los más antiguos primero, hasta {@code limit}.
     */
    List<Payment> findAwaitingBefore(Instant staleBefore, int limit);

    /**
     * Reclama un cobro del barrido: vuelve a estampar awaitingSince con una actualización CONDICIONAL (solo si sigue
     * valiendo {@code expected}) que confirma en su propia transacción.
     *
     * @return true si este llamante ganó el reclamo; false si otra réplica o petición lo tocó antes
     */
    boolean claimAwaiting(UUID id, Instant expected, Instant renewed);

    void deleteById(UUID id);
}
