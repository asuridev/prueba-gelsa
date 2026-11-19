package com.ecomerce.payment.infrastructure.persistence.repositories;

import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.ecomerce.payment.infrastructure.persistence.entities.PaymentJpa;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpa, UUID>, JpaSpecificationExecutor<PaymentJpa> {

    @EntityGraph(attributePaths = { "refunds" })
    Optional<PaymentJpa> findByChargeRequestId(String chargeRequestId);

    @EntityGraph(attributePaths = { "refunds" })
    @Override
    Optional<PaymentJpa> findById(UUID id);

    /** Candidatos del barrido: los que esperan desenlace desde antes del corte, los más antiguos primero. */
    @Query("select p from PaymentJpa p where p.status in :statuses and p.awaitingSince < :staleBefore order by p.awaitingSince asc, p.id asc")
    List<PaymentJpa> findAwaiting(@Param("statuses") Collection<PaymentStatus> statuses,
            @Param("staleBefore") Instant staleBefore, Pageable limit);

    /** El reclamo del barrido: condicional, en su propia transacción. */
    @Modifying
    @Transactional
    @Query("update PaymentJpa p set p.awaitingSince = :renewed where p.id = :id and p.awaitingSince = :expected")
    int claimAwaiting(@Param("id") UUID id, @Param("expected") Instant expected, @Param("renewed") Instant renewed);
}
