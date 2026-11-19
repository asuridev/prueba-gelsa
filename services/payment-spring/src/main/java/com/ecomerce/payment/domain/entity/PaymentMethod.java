package com.ecomerce.payment.domain.entity;

import com.ecomerce.payment.domain.enums.PaymentMethodStatus;
import com.ecomerce.payment.domain.errors.InvariantViolatedException;
import com.ecomerce.payment.domain.identity.Uuids;
import com.ecomerce.payment.domain.valueobject.CardDetails;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Un medio de pago guardado de un titular, para cobrarle sin que esté presente.
 */
public class PaymentMethod {

    private UUID id = Uuids.v7();

    // Referencia opaca del medio en la pasarela; nunca sale del servicio.
    private String gatewayMethodRef;

    private CardDetails card;

    // Medio preferente del titular.
    private Boolean isDefault = false;

    private PaymentMethodStatus status = PaymentMethodStatus.ACTIVE;

    // Derivado: true si la tarjeta caducó antes del mes en curso en UTC (vale hasta el último día de su mes); lo calcula getExpired().
    private Boolean expired;

    /** Alta de un medio en active (lo llama su Wallet). */
    public static PaymentMethod create(UUID id, String gatewayMethodRef, CardDetails card, boolean isDefault) {
        if (gatewayMethodRef == null || gatewayMethodRef.isBlank()) {
            throw new InvariantViolatedException("PaymentMethod: gatewayMethodRef es obligatorio.");
        }
        Objects.requireNonNull(card, "card");
        return new PaymentMethod(id, gatewayMethodRef, card, isDefault, PaymentMethodStatus.ACTIVE, null);
    }

    /** Lo gobierna su Wallet: marca el medio como preferente. Un medio removed no puede serlo. */
    public void markDefault() {
        if (status != PaymentMethodStatus.ACTIVE) {
            throw new InvariantViolatedException("PaymentMethod: solo un medio active puede ser el preferente.");
        }
        this.isDefault = true;
    }

    /** Lo gobierna su Wallet: el medio deja de ser el preferente. */
    public void unmarkDefault() {
        this.isDefault = false;
    }

    /** active → removed (terminal): un medio retirado nunca es el preferente. */
    public void remove() {
        this.status = PaymentMethodStatus.REMOVED;
        this.isDefault = false;
    }

    public boolean isActive() {
        return status == PaymentMethodStatus.ACTIVE;
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public PaymentMethod(UUID id, String gatewayMethodRef, CardDetails card, Boolean isDefault, PaymentMethodStatus status, Boolean expired) {
        this.id = id;
        this.gatewayMethodRef = gatewayMethodRef;
        this.card = card;
        this.isDefault = isDefault;
        this.status = status;
        this.expired = expired;
    }

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (gatewayMethodRef != null && (gatewayMethodRef.codePointCount(0, gatewayMethodRef.length()) > 255)) {
            throw new InvariantViolatedException("PaymentMethod: gatewayMethodRef queda fuera de sus cotas.");
        }
        // Sus checks (domain: entities.PaymentMethod.checks).
        // check 0: isDefault = false cuando status es removed.
        if (status == PaymentMethodStatus.REMOVED && !(Objects.isNull(isDefault) || Objects.equals(isDefault, false))) {
            throw new InvariantViolatedException("PaymentMethod: isDefault = false cuando status es removed.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getGatewayMethodRef() {
        return gatewayMethodRef;
    }

    public CardDetails getCard() {
        return card;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public PaymentMethodStatus getStatus() {
        return status;
    }

    public Boolean getExpired() {
        return card != null && YearMonth.of(card.expYear(), card.expMonth()).isBefore(YearMonth.now(ZoneOffset.UTC));
    }
}
