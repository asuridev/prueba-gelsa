package com.ecomerce.payment.domain.entity;

import com.ecomerce.payment.domain.enums.RefundStatus;
import com.ecomerce.payment.domain.errors.InvariantViolatedException;
import com.ecomerce.payment.domain.identity.Uuids;
import com.ecomerce.payment.domain.valueobject.RequestReferenceFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una devolución, total o parcial, de un cobro capturado.
 */
public class Refund {

    private UUID id = Uuids.v7();

    // Clave de negocio de la devolución; dos peticiones con la misma son la misma devolución.
    private String refundRequestId;

    // Importe pedido; si la pasarela informa al completarla un importe devuelto distinto, se guarda el informado, sin pasar de lo que quedaba por devolver del cobro.
    private BigDecimal amount;

    // Motivo libre que informa el consumidor, para su propio rastro.
    private String reason;

    private RefundStatus status = RefundStatus.PENDING;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    /**
     * Una devolución nace en pending cuando el cobro entra en refunding (lo gobierna la raíz Payment). La escala
     * según la moneda y el saldo los comprueba la raíz, que conoce la moneda y las demás devoluciones.
     */
    public static Refund create(String refundRequestId, BigDecimal amount, String reason) {
        RequestReferenceFormat.validate(refundRequestId);
        Instant now = Instant.now();
        return new Refund(Uuids.v7(), refundRequestId, amount, reason, RefundStatus.PENDING, now, now);
    }

    /** pending → succeeded con el importe que informa la pasarela (lo llama la raíz). */
    public void succeed(BigDecimal confirmedAmount) {
        requirePending();
        this.amount = confirmedAmount;
        this.status = RefundStatus.SUCCEEDED;
    }

    /** pending → failed (lo llama la raíz). */
    public void fail() {
        requirePending();
        this.status = RefundStatus.FAILED;
    }

    /** Una devolución succeeded o failed no vuelve a cambiar de status. */
    private void requirePending() {
        if (status != RefundStatus.PENDING) {
            throw new InvariantViolatedException("Refund: una devolución " + status + " no vuelve a cambiar de status.");
        }
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public Refund(UUID id, String refundRequestId, BigDecimal amount, String reason, RefundStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.refundRequestId = refundRequestId;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private static final Pattern REFUND_REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (refundRequestId != null && ((refundRequestId.codePointCount(0, refundRequestId.length()) < 1 || refundRequestId.codePointCount(0, refundRequestId.length()) > 100) || (!REFUND_REQUEST_ID_PATTERN.matcher(refundRequestId).matches()))) {
            throw new InvariantViolatedException("Refund: refundRequestId queda fuera de sus cotas.");
        }
        if (amount != null && (amount.compareTo(new BigDecimal("0.001")) < 0)) {
            throw new InvariantViolatedException("Refund: amount queda fuera de sus cotas.");
        }
        if (reason != null && (reason.codePointCount(0, reason.length()) > 200)) {
            throw new InvariantViolatedException("Refund: reason queda fuera de sus cotas.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRefundRequestId() {
        return refundRequestId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
