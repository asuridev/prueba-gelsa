package com.ecomerce.payment.infrastructure.persistence.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * La tabla del historial, mapeada SOLO para el esquema: así la crea Hibernate en `local` y `test`, la valida en
 * `develop` y `production`, y entra en el baseline que exporta el agente (o en el paso de esquema de build, si la base
 * ya está desplegada). Nadie la lee por JPA: la usa {@code JdbcChangeHistory}, con las sentencias de keel-core.
 *
 * <p>Columnas e índice son los de keel-core ({@code CHANGE_HISTORY}): los comparten los cuatro generadores.
 */
@Entity
@Table(name = "change_history", indexes = @Index(name = "ix_change_history_root", columnList = "root_type, root_id, at, id"))
public class ChangeHistoryJpa {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "root_type", nullable = false, length = 100)
    private String rootType;

    @Column(name = "root_id", nullable = false, length = 64)
    private String rootId;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "author", nullable = false, length = 255)
    private String author;

    @Column(name = "change_kind", nullable = false, length = 16)
    private String changeKind;

    @Column(name = "changes", nullable = false, columnDefinition = "text")
    private String changes;

    protected ChangeHistoryJpa() {
    }
}
