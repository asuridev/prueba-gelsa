package com.ecomerce.notifications.domain.aggregate;

import com.ecomerce.notifications.domain.enums.SuppressionReason;
import com.ecomerce.notifications.domain.enums.SuppressionStatus;
import com.ecomerce.notifications.domain.errors.InvalidStateTransitionException;
import com.ecomerce.notifications.domain.errors.InvariantViolatedException;
import com.ecomerce.notifications.domain.identity.Uuids;
import com.ecomerce.notifications.domain.valueobject.EmailAddressFormat;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Dirección a la que el servicio no envía. Una dirección tiene un único registro para siempre: liberarla lo conserva y una nueva supresión lo reactiva.
 */
public class SuppressedAddress {

    private UUID id = Uuids.v7();

    private String address;

    private SuppressionReason reason;

    private String notes;

    private SuppressionStatus status = SuppressionStatus.ACTIVE;

    private Instant suppressedAt;

    private Instant releasedAt;

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /** Factory de creación: nace active con suppressedAt ahora; la dirección se normaliza a minúsculas. */
    public static SuppressedAddress create(String address, SuppressionReason reason, String notes, Instant now) {
        String normalized = address == null ? null : address.toLowerCase(java.util.Locale.ROOT);
        EmailAddressFormat.validate(normalized);
        return new SuppressedAddress(Uuids.v7(), normalized, reason, notes, SuppressionStatus.ACTIVE, now, null, null);
    }

    public boolean isActive() {
        return status == SuppressionStatus.ACTIVE;
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public SuppressedAddress(UUID id, String address, SuppressionReason reason, String notes, SuppressionStatus status, Instant suppressedAt, Instant releasedAt, Long lockVersion) {
        this.id = id;
        this.address = address;
        this.reason = reason;
        this.notes = notes;
        this.status = status;
        this.suppressedAt = suppressedAt;
        this.releasedAt = releasedAt;
        this.lockVersion = lockVersion;
    }

    // Transiciones válidas del lifecycle del diseño; un estado con Set.of() es terminal.
    private static final Map<SuppressionStatus, Set<SuppressionStatus>> TRANSITIONS = Map.of(
        SuppressionStatus.ACTIVE, Set.of(SuppressionStatus.RELEASED),
        SuppressionStatus.RELEASED, Set.of(SuppressionStatus.ACTIVE)
    );

    /** ACTIVE → RELEASED (releaseAddress): releasedAt queda con la fecha de la liberación. */
    public void release(Instant now) {
        transitionTo(SuppressionStatus.RELEASED);
        this.releasedAt = now;
    }

    /**
     * RELEASED → ACTIVE (suppressAddress, reportEmailBounce): reactiva el registro con el reason nuevo,
     * suppressedAt ahora, releasedAt vacío y las notas reemplazadas.
     */
    public void reactivate(SuppressionReason reason, String notes, Instant now) {
        transitionTo(SuppressionStatus.ACTIVE);
        this.reason = reason;
        this.notes = notes;
        this.suppressedAt = now;
        this.releasedAt = null;
    }

    // Guard interno del lifecycle: lo llaman los métodos semánticos, nunca un handler.
    private void transitionTo(SuppressionStatus target) {
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(status.name(), target.name());
        }
        this.status = target;
    }

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (address != null && ((address.codePointCount(0, address.length()) > 254) || (!ADDRESS_PATTERN.matcher(address).matches()))) {
            throw new InvariantViolatedException("SuppressedAddress: address queda fuera de sus cotas.");
        }
        if (notes != null && (notes.codePointCount(0, notes.length()) > 1000)) {
            throw new InvariantViolatedException("SuppressedAddress: notes queda fuera de sus cotas.");
        }
        // Sus checks (domain: entities.SuppressedAddress.checks).
        // check 0: releasedAt es obligatorio cuando status es released.
        if (status == SuppressionStatus.RELEASED && (releasedAt == null)) {
            throw new InvariantViolatedException("SuppressedAddress: releasedAt es obligatorio cuando status es released.");
        }
        // check 1: releasedAt tiene que estar vacío cuando status es active.
        if (status == SuppressionStatus.ACTIVE && !(releasedAt == null)) {
            throw new InvariantViolatedException("SuppressedAddress: releasedAt tiene que estar vacío cuando status es active.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public SuppressionReason getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }

    public SuppressionStatus getStatus() {
        return status;
    }

    public Instant getSuppressedAt() {
        return suppressedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
