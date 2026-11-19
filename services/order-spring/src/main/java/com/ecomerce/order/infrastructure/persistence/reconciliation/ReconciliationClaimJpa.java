package com.ecomerce.order.infrastructure.persistence.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Marca de que una réplica se llevó un candidato del barrido de reconciliación.
 *
 * <p>La clave es compuesta —activación + entidad— porque una misma entidad puede estar
 * esperando el desenlace de VARIAS activaciones a la vez, y cada barrido reclama la suya.
 * Con una marca compartida, el segundo encargo pisaría el reclamo del primero.
 *
 * <p><b>La marca caduca</b>, y por eso {@code claimed_at} es un instante y no un booleano:
 * un lock de base de datos se suelta cuando muere la conexión, pero una fila no. Sin plazo,
 * la réplica que muera entre el reclamo y el desenlace retendría el candidato para siempre.
 *
 * <p><b>Implementa {@link Persistable}</b>, y de eso depende que el reclamo funcione. Con la
 * clave ASIGNADA y sin esto, {@code SimpleJpaRepository.isNew()} ve el id no nulo, deduce
 * {@code merge()} —SELECT + UPDATE— y la inserción NUNCA viola la clave primaria: las dos
 * réplicas creerían haber reclamado, en silencio.
 */
@Entity
@Table(name = "reconciliation_claim", indexes = {
        @Index(name = "ix_reconciliation_claim_claimed_at", columnList = "claimed_at")
})
public class ReconciliationClaimJpa implements Persistable<ReconciliationClaimJpa.ReconciliationClaimId> {

    @EmbeddedId
    private ReconciliationClaimId id;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    /** Se pone a true al leer la fila de la base ({@code @PostLoad}). */
    @Transient
    private boolean persisted;

    protected ReconciliationClaimJpa() {
    }

    public ReconciliationClaimJpa(ReconciliationClaimId id, Instant claimedAt) {
        this.id = id;
        this.claimedAt = claimedAt;
    }

    @Override
    public ReconciliationClaimId getId() {
        return id;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    void markPersisted() {
        this.persisted = true;
    }

    @Embeddable
    public static class ReconciliationClaimId implements Serializable {

        @Column(name = "activation", nullable = false, length = 120)
        private String activation;

        @Column(name = "entity_id", nullable = false)
        private UUID entityId;

        protected ReconciliationClaimId() {
        }

        public ReconciliationClaimId(String activation, UUID entityId) {
            this.activation = activation;
            this.entityId = entityId;
        }

        public String getActivation() {
            return activation;
        }

        public UUID getEntityId() {
            return entityId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ReconciliationClaimId that)) {
                return false;
            }
            return Objects.equals(activation, that.activation) && Objects.equals(entityId, that.entityId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(activation, entityId);
        }
    }
}
