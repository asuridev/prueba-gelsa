package com.ecomerce.payment.infrastructure.persistence.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Persistable;

/**
 * Petición ya atendida: un par (operación, clave de idempotencia).
 *
 * La clave es compuesta a propósito: la misma cabecera puede llegar a dos
 * operaciones distintas y cada una deduplica por su cuenta. La unicidad la
 * impone la clave primaria, no una consulta previa: es la BD la que arbitra la
 * carrera entre dos reintentos simultáneos, y quien la pierde revierte.
 *
 * La caducidad se guarda calculada (expires_at) en vez de deducirla del TTL al
 * consultar: el ttlSeconds del diseño puede cambiar entre despliegues y las
 * filas ya escritas conservan la ventana con la que se registraron.
 *
 * <p>El índice sobre expires_at es de la purga, no de la lectura (que va por clave
 * primaria): el barrido borra por rango y sin él cada réplica recorre la tabla
 * entera. Mismo nombre que su equivalente documental, para que las dos ramas se
 * comparen.
 */
@Entity
@Table(name = "idempotency_record", indexes = {
        @Index(name = "ix_idempotency_record_expires_at", columnList = "expires_at")
})
public class IdempotencyRecordJpa implements Persistable<IdempotencyRecordJpa.IdempotencyRecordId> {

    @EmbeddedId
    private IdempotencyRecordId id;

    /** Representación determinista del contenido con el que se usó la clave. */
    @Column(name = "signature", nullable = false, length = 128)
    private String signature;

    /**
     * Id del recurso resultante; null si la operación no crea ninguno.
     *
     * <p>255 y no 64: un id de dominio no siempre es un uuid, y quedarse corto no
     * falla al validar sino al guardar — con la operación ya ejecutada.
     */
    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Se pone a true al leer la fila de la base ({@code @PostLoad}). */
    @Transient
    private boolean persisted;

    protected IdempotencyRecordJpa() {
        // Requerido por JPA.
    }

    public IdempotencyRecordJpa(IdempotencyRecordId id, String signature, String resourceId, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.signature = signature;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    @Override
    public IdempotencyRecordId getId() {
        return id;
    }

    /**
     * Nueva mientras no se haya leido de la base. NO es constante, y el matiz es el
     * que hace que esto funcione: {@code SimpleJpaRepository.delete(...)} empieza con
     * {@code if (isNew(entity)) return;}, asi que un {@code isNew()} que devuelva
     * siempre {@code true} convierte el borrado en un NO-OP SILENCIOSO — y este
     * adaptador necesita borrar, para retirar un registro caducado.
     *
     * <p>Con el flag por {@code @PostLoad}: una fila recien construida es nueva
     * —{@code persist}, y la clave primaria arbitra la carrera entre dos peticiones
     * simultaneas— y una fila leida de la base no lo es, asi que se puede borrar.
     */
    @Override
    @Transient
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    public String getSignature() {
        return signature;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Clave compuesta (operación, clave de idempotencia). */
    @Embeddable
    public static class IdempotencyRecordId implements Serializable {

        /** El ámbito de la clave (idempotencyScope() del comando). La columna no se llama "scope": lo es en SQL estándar. */
        @Column(name = "operation_scope", nullable = false, length = 128)
        private String scope;

        @Column(name = "idempotency_key", nullable = false, length = 255)
        private String idempotencyKey;

        protected IdempotencyRecordId() {
            // Requerido por JPA.
        }

        public IdempotencyRecordId(String scope, String idempotencyKey) {
            this.scope = scope;
            this.idempotencyKey = idempotencyKey;
        }

        public String getScope() {
            return scope;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            IdempotencyRecordId that = (IdempotencyRecordId) other;
            return Objects.equals(scope, that.scope) && Objects.equals(idempotencyKey, that.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scope, idempotencyKey);
        }
    }
}
