package com.ecomerce.payment.infrastructure.persistence.timer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * La tabla del cronómetro, mapeada SOLO para el esquema: así la crea Hibernate en `local` y `test`, la valida en
 * `develop` y `production`, y entra en el baseline que exporta el agente (o en el paso de esquema de build, si la
 * base ya está desplegada). Nadie la lee por JPA: la usa {@code JdbcTimerStore}, con las sentencias de keel-core.
 *
 * <p>Columnas, unicidad e índice son los de keel-core ({@code KEEL_TIMER}): los comparten los cuatro generadores.
 */
@Entity
@Table(name = "keel_timer",
        uniqueConstraints = @UniqueConstraint(name = "uq_keel_timer_kind_entity", columnNames = {"kind", "entity_id"}),
        indexes = @Index(name = "ix_keel_timer_due_at", columnList = "due_at"))
public class KeelTimerJpa {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "kind", nullable = false, length = 160)
    private String kind;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "spec", length = 120)
    private String spec;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "parked_at")
    private Instant parkedAt;

    protected KeelTimerJpa() {
    }
}
