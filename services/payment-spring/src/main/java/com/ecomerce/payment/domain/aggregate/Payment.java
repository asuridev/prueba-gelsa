package com.ecomerce.payment.domain.aggregate;

import com.ecomerce.payment.domain.entity.Refund;
import com.ecomerce.payment.domain.enums.FailureReason;
import com.ecomerce.payment.domain.enums.PaymentActionRejectedAction;
import com.ecomerce.payment.domain.enums.PaymentCancelOrigin;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.ecomerce.payment.domain.enums.RefundStatus;
import com.ecomerce.payment.domain.errors.AmountScaleInvalidError;
import com.ecomerce.payment.domain.errors.CurrencyUnknownError;
import com.ecomerce.payment.domain.errors.InvalidStateTransitionException;
import com.ecomerce.payment.domain.errors.InvariantViolatedException;
import com.ecomerce.payment.domain.errors.PaymentNotRefundableError;
import com.ecomerce.payment.domain.errors.RefundAmountExceededError;
import com.ecomerce.payment.domain.events.DomainEvent;
import com.ecomerce.payment.domain.events.PaymentActionRejectedEvent;
import com.ecomerce.payment.domain.events.PaymentActionRequiredEvent;
import com.ecomerce.payment.domain.events.PaymentAuthorizedEvent;
import com.ecomerce.payment.domain.events.PaymentCanceledEvent;
import com.ecomerce.payment.domain.events.PaymentCapturedEvent;
import com.ecomerce.payment.domain.events.PaymentFailedEvent;
import com.ecomerce.payment.domain.events.PaymentRefundedEvent;
import com.ecomerce.payment.domain.identity.Uuids;
import com.ecomerce.payment.domain.valueobject.CurrencyCodeFormat;
import com.ecomerce.payment.domain.valueobject.RequestReferenceFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un cobro con tarjeta pedido por un servidor consumidor: se autoriza, se captura o se anula, y admite devoluciones parciales. Es la memoria del servicio frente a la pasarela.
 *
 * <p>Invariantes del diseño y dónde se guardan:
 * <ul>
 *   <li>la moneda no cambia nunca: ningún método la asigna tras el factory;</li>
 *   <li>los decimales de amount no superan los de la moneda (ISO 4217): {@link #requireKnownCurrency} y
 *       {@link #requireAmountScale} en el factory y en {@link #startRefund};</li>
 *   <li>a lo sumo una devolución pending, y solo en refunding: la crea {@link #startRefund} y la resuelven
 *       {@link #markRefunded} / {@link #refundRejected};</li>
 *   <li>paymentMethodId pertenece al Wallet del titular: lo comprueba el handler de requestCharge, porque cruza
 *       de agregado.</li>
 * </ul>
 */
public class Payment {

    // ─── Eventos de dominio ───────────────────────────────────────────────────
    // Se acumulan aquí y salen por pullDomainEvents() al persistir; nadie más
    // construye eventos de este agregado.
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    /** Vacía el buffer y devuelve lo acumulado; lo llama el adaptador de repositorio. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pending = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return pending;
    }

    private UUID id = Uuids.v7();

    // Clave de negocio del cobro; dos peticiones con la misma son el mismo cobro.
    private String chargeRequestId;

    // Titular del cobro.
    private String customerRef;

    // Importe autorizado; al capturar se cobra entero. Lo fija el servidor consumidor, que es de confianza: el servicio no lo contrasta con nada.
    private BigDecimal amount;

    private String currency;

    private PaymentStatus status = PaymentStatus.PENDING;

    // Id que asigna la pasarela; vacío mientras no ha contestado.
    private String gatewayPaymentId;

    // Desde cuándo espera sin respuesta un desenlace de la pasarela; solo tiene valor en estados de espera.
    private Instant awaitingSince;

    // Por qué falló el cobro; solo en failed.
    private FailureReason failureReason;

    // Acción opaca que el cliente debe completar (3DS); en actionRequired, y se conserva mientras se anula un cobro que venía de actionRequired.
    private String customerAction;

    // Desde cuándo espera el cobro la autenticación del cliente; lo estampa markActionRequired al entrar en actionRequired desde pending, y no lo renuevan las consultas del barrido ni la vuelta a actionRequired desde canceling (se conserva el de antes). Es el reloj del plazo del 3DS.
    private Instant actionRequiredSince;

    // Estado del que salió el cobro al entrar en canceling; solo en canceling. Es a donde vuelve si la pasarela rechaza la anulación.
    private PaymentCancelOrigin cancelOrigin;

    // Medio guardado con el que se cobró; vacío si se cobró con un token de un solo uso. Es un valor y no una relación: el medio vive dentro del agregado Wallet.
    private UUID paymentMethodId;

    // Derivado: suma de las devoluciones succeeded (cero si no hay ninguna); lo recalcula recomputeRefunded().
    private BigDecimal refundedAmount;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    private List<Refund> refunds = new ArrayList<>();

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /**
     * Alta de un cobro (requestCharge): nace en pending con awaitingSince estampado, antes de llamar a la pasarela.
     * Aplica el formato de los value types, que la moneda sea un código vigente de ISO 4217 y que el importe no
     * tenga más decimales que ella.
     */
    public static Payment create(String chargeRequestId, String customerRef, BigDecimal amount, String currency, UUID paymentMethodId) {
        RequestReferenceFormat.validate(chargeRequestId);
        CurrencyCodeFormat.validate(currency);
        requireKnownCurrency(currency);
        requireAmountScale(amount, currency);
        Instant now = Instant.now();
        return new Payment(Uuids.v7(), chargeRequestId, customerRef, amount, currency, PaymentStatus.PENDING, null, now, null,
                null, null, null, paymentMethodId, BigDecimal.ZERO, now, now, List.of(), null);
    }

    /** La moneda es un código vigente de ISO 4217 con unidad menor (CURRENCY_UNKNOWN en otro caso). */
    public static void requireKnownCurrency(String currency) {
        if (fractionDigits(currency) < 0) {
            throw new CurrencyUnknownError("La moneda no es un código vigente de ISO 4217: " + currency);
        }
    }

    /** El importe no tiene más decimales que los de la moneda (AMOUNT_SCALE_INVALID en otro caso). */
    public static void requireAmountScale(BigDecimal amount, String currency) {
        if (amount == null) {
            return;
        }
        int digits = fractionDigits(currency);
        if (digits < 0) {
            throw new CurrencyUnknownError("La moneda no es un código vigente de ISO 4217: " + currency);
        }
        if (amount.stripTrailingZeros().scale() > digits) {
            throw new AmountScaleInvalidError("El importe tiene más decimales de los que admite " + currency + " (" + digits + ")");
        }
    }

    private static int fractionDigits(String currency) {
        try {
            return Currency.getInstance(currency).getDefaultFractionDigits();
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return -1;
        }
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public Payment(UUID id, String chargeRequestId, String customerRef, BigDecimal amount, String currency, PaymentStatus status, String gatewayPaymentId, Instant awaitingSince, FailureReason failureReason, String customerAction, Instant actionRequiredSince, PaymentCancelOrigin cancelOrigin, UUID paymentMethodId, BigDecimal refundedAmount, Instant createdAt, Instant updatedAt, List<Refund> refunds, Long lockVersion) {
        this.id = id;
        this.chargeRequestId = chargeRequestId;
        this.customerRef = customerRef;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.gatewayPaymentId = gatewayPaymentId;
        this.awaitingSince = awaitingSince;
        this.failureReason = failureReason;
        this.customerAction = customerAction;
        this.actionRequiredSince = actionRequiredSince;
        this.cancelOrigin = cancelOrigin;
        this.paymentMethodId = paymentMethodId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.refunds = new ArrayList<>(refunds);
        this.lockVersion = lockVersion;
        recomputeRefunded();
    }

    // Transiciones válidas del lifecycle del diseño; un estado con Set.of() es terminal.
    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
        PaymentStatus.PENDING, Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.ACTION_REQUIRED, PaymentStatus.FAILED, PaymentStatus.CANCELED),
        PaymentStatus.ACTION_REQUIRED, Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED, PaymentStatus.CANCELING, PaymentStatus.CANCELED),
        PaymentStatus.AUTHORIZED, Set.of(PaymentStatus.CAPTURING, PaymentStatus.CANCELING, PaymentStatus.CANCELED),
        PaymentStatus.CAPTURING, Set.of(PaymentStatus.CAPTURED, PaymentStatus.AUTHORIZED, PaymentStatus.CANCELED),
        PaymentStatus.CANCELING, Set.of(PaymentStatus.CANCELED, PaymentStatus.AUTHORIZED, PaymentStatus.ACTION_REQUIRED, PaymentStatus.FAILED),
        PaymentStatus.CAPTURED, Set.of(PaymentStatus.REFUNDING),
        PaymentStatus.REFUNDING, Set.of(PaymentStatus.CAPTURED, PaymentStatus.REFUNDED),
        PaymentStatus.REFUNDED, Set.of(),
        PaymentStatus.CANCELED, Set.of(),
        PaymentStatus.FAILED, Set.of()
    );

    // ─── Acciones que el consumidor pide: pasan al estado en vuelo ANTES de llamar a la pasarela ───

    /** AUTHORIZED → CAPTURING (capturePayment): el cobro espera la respuesta de la captura. */
    public void startCapture() {
        transitionTo(PaymentStatus.CAPTURING);
        this.awaitingSince = Instant.now();
    }

    /**
     * AUTHORIZED | ACTION_REQUIRED → CANCELING (cancelPayment, y el plazo del 3DS en sweepPendingPayments): anota el
     * estado del que sale; conserva customerAction y actionRequiredSince si venía de actionRequired.
     */
    public void startCancel() {
        PaymentCancelOrigin origin = switch (status) {
            case AUTHORIZED -> PaymentCancelOrigin.AUTHORIZED;
            case ACTION_REQUIRED -> PaymentCancelOrigin.ACTION_REQUIRED;
            default -> throw new InvalidStateTransitionException(status.name(), PaymentStatus.CANCELING.name());
        };
        transitionTo(PaymentStatus.CANCELING);
        this.cancelOrigin = origin;
        this.awaitingSince = Instant.now();
    }

    /**
     * CAPTURED → REFUNDING (refundPayment): crea la devolución en pending. Sin importe, devuelve todo lo que queda.
     * Orden de guardas del diseño: estado, decimales según la moneda, saldo.
     */
    public Refund startRefund(String refundRequestId, BigDecimal requested, String reason) {
        RequestReferenceFormat.validate(refundRequestId);
        if (status != PaymentStatus.CAPTURED) {
            throw new PaymentNotRefundableError("El cobro no está capturado: no admite devolución");
        }
        requireAmountScale(requested, currency);
        BigDecimal remaining = remainingToRefund();
        BigDecimal refundAmount = requested != null ? requested : remaining;
        if (refundAmount.compareTo(remaining) > 0) {
            throw new RefundAmountExceededError("El importe supera lo que queda por devolver");
        }
        Refund refund = Refund.create(refundRequestId, refundAmount, reason);
        refunds.add(refund);
        transitionTo(PaymentStatus.REFUNDING);
        this.awaitingSince = Instant.now();
        return refund;
    }

    // ─── La pasarela rechazó la acción (o nunca la recibió): vuelta al estado del que salió ───

    /** CAPTURING → AUTHORIZED: la captura no se hizo. Publica PaymentActionRejected. */
    public boolean captureRejected() {
        if (status != PaymentStatus.CAPTURING) {
            return false;
        }
        transitionTo(PaymentStatus.AUTHORIZED);
        this.awaitingSince = null;
        raise(PaymentActionRejectedEvent.of(id, chargeRequestId, customerRef, PaymentActionRejectedAction.CAPTURE, null, null, status));
        return true;
    }

    /**
     * CANCELING → cancelOrigin: la anulación no se hizo. Al volver a actionRequired se renueva awaitingSince y se
     * conserva actionRequiredSince; al volver a authorized se vacía. Publica PaymentActionRejected.
     */
    public boolean cancelRejected() {
        if (status != PaymentStatus.CANCELING) {
            return false;
        }
        PaymentStatus target = cancelOrigin == PaymentCancelOrigin.ACTION_REQUIRED ? PaymentStatus.ACTION_REQUIRED : PaymentStatus.AUTHORIZED;
        transitionTo(target);
        this.awaitingSince = target == PaymentStatus.ACTION_REQUIRED ? Instant.now() : null;
        this.cancelOrigin = null;
        if (target == PaymentStatus.AUTHORIZED) {
            this.customerAction = null;
            this.actionRequiredSince = null;
        }
        raise(PaymentActionRejectedEvent.of(id, chargeRequestId, customerRef, PaymentActionRejectedAction.CANCEL, null, null, status));
        return true;
    }

    /** REFUNDING → CAPTURED: la devolución pendiente pasa a failed. Publica PaymentActionRejected. */
    public boolean refundRejected() {
        if (status != PaymentStatus.REFUNDING) {
            return false;
        }
        Refund pending = pendingRefund().orElseThrow(() -> new InvariantViolatedException("Payment: refunding sin devolución pending."));
        pending.fail();
        transitionTo(PaymentStatus.CAPTURED);
        this.awaitingSince = null;
        recomputeRefunded();
        raise(PaymentActionRejectedEvent.of(id, chargeRequestId, customerRef, PaymentActionRejectedAction.REFUND,
                pending.getRefundRequestId(), pending.getAmount(), status));
        return true;
    }

    // ─── Desenlaces de la pasarela: idempotentes (false = ya no estaba en un estado de origen) ───

    /** PENDING | ACTION_REQUIRED → AUTHORIZED (markAuthorized). */
    public boolean markAuthorized(String gatewayPaymentId) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.ACTION_REQUIRED) {
            return false;
        }
        transitionTo(PaymentStatus.AUTHORIZED);
        this.gatewayPaymentId = gatewayPaymentId;
        this.awaitingSince = null;
        this.customerAction = null;
        this.actionRequiredSince = null;
        raise(PaymentAuthorizedEvent.of(id, chargeRequestId, customerRef, amount, currency, gatewayPaymentId));
        return true;
    }

    /** PENDING → ACTION_REQUIRED (markActionRequired): sigue esperando, ahora al cliente. */
    public boolean markActionRequired(String gatewayPaymentId, String customerAction) {
        if (status != PaymentStatus.PENDING) {
            return false;
        }
        transitionTo(PaymentStatus.ACTION_REQUIRED);
        Instant now = Instant.now();
        this.gatewayPaymentId = gatewayPaymentId;
        this.customerAction = customerAction;
        this.awaitingSince = now;
        this.actionRequiredSince = now;
        raise(PaymentActionRequiredEvent.of(id, chargeRequestId, customerRef, amount, currency, customerAction));
        return true;
    }

    /** CAPTURING → CAPTURED (markCaptured). */
    public boolean markCaptured() {
        if (status != PaymentStatus.CAPTURING) {
            return false;
        }
        transitionTo(PaymentStatus.CAPTURED);
        this.awaitingSince = null;
        raise(PaymentCapturedEvent.of(id, chargeRequestId, customerRef, amount, currency));
        return true;
    }

    /** PENDING | ACTION_REQUIRED | CANCELING → FAILED (markFailed). */
    public boolean markFailed(String gatewayPaymentId, FailureReason reason) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.ACTION_REQUIRED && status != PaymentStatus.CANCELING) {
            return false;
        }
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = Objects.requireNonNull(reason, "failureReason");
        if (gatewayPaymentId != null) {
            this.gatewayPaymentId = gatewayPaymentId;
        }
        clearWaiting();
        raise(PaymentFailedEvent.of(id, chargeRequestId, customerRef, amount, currency, failureReason));
        return true;
    }

    /**
     * PENDING | ACTION_REQUIRED | AUTHORIZED | CAPTURING | CANCELING → CANCELED (markCanceled). El evento lleva
     * requested a true si venía de canceling (lo pidió el consumidor o venció el plazo del 3DS).
     */
    public boolean markCanceled(String gatewayPaymentId) {
        if (!TRANSITIONS.get(status).contains(PaymentStatus.CANCELED)) {
            return false;
        }
        boolean requested = status == PaymentStatus.CANCELING;
        transitionTo(PaymentStatus.CANCELED);
        if (gatewayPaymentId != null) {
            this.gatewayPaymentId = gatewayPaymentId;
        }
        clearWaiting();
        raise(PaymentCanceledEvent.of(id, chargeRequestId, customerRef, amount, currency, requested));
        return true;
    }

    /**
     * REFUNDING → CAPTURED | REFUNDED (markRefunded). La devolución pending pasa a succeeded con el importe que
     * informa la pasarela, sin pasar de lo que quedaba por devolver; el cobro pasa a refunded si lo devuelto llega
     * a amount.
     */
    public boolean markRefunded(BigDecimal informedAmount) {
        if (status != PaymentStatus.REFUNDING) {
            return false;
        }
        Refund pending = pendingRefund().orElseThrow(() -> new InvariantViolatedException("Payment: refunding sin devolución pending."));
        BigDecimal remaining = remainingToRefund();
        pending.succeed(informedAmount.min(remaining));
        recomputeRefunded();
        this.awaitingSince = null;
        transitionTo(refundedAmount.compareTo(amount) == 0 ? PaymentStatus.REFUNDED : PaymentStatus.CAPTURED);
        raise(PaymentRefundedEvent.of(id, chargeRequestId, customerRef, pending.getRefundRequestId(), pending.getAmount(),
                refundedAmount, currency, status));
        return true;
    }

    // ─── Consultas del propio agregado ────────────────────────────────────────

    /** La devolución con esa clave de negocio, si es de este cobro. */
    public Optional<Refund> findRefund(String refundRequestId) {
        return refunds.stream().filter(refund -> refund.getRefundRequestId().equals(refundRequestId)).findFirst();
    }

    /** Lo que queda por devolver: amount menos lo devuelto (succeeded). */
    public BigDecimal remainingToRefund() {
        return amount.subtract(refundedAmount);
    }

    private Optional<Refund> pendingRefund() {
        return refunds.stream().filter(refund -> refund.getStatus() == RefundStatus.PENDING).findFirst();
    }

    private void recomputeRefunded() {
        this.refundedAmount = refunds.stream()
                .filter(refund -> refund.getStatus() == RefundStatus.SUCCEEDED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Un cobro conserva solo lo que sigue siendo verdad: al terminar se vacían las marcas de espera. */
    private void clearWaiting() {
        this.awaitingSince = null;
        this.customerAction = null;
        this.actionRequiredSince = null;
        this.cancelOrigin = null;
    }

    // Guard interno del lifecycle: lo llaman los métodos semánticos, nunca un handler.
    private void transitionTo(PaymentStatus target) {
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(status.name(), target.name());
        }
        this.status = target;
    }

    private static final Pattern CHARGE_REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        for (Refund child : refunds) {
            child.ensureInvariants();
        }
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (chargeRequestId != null && ((chargeRequestId.codePointCount(0, chargeRequestId.length()) < 1 || chargeRequestId.codePointCount(0, chargeRequestId.length()) > 100) || (!CHARGE_REQUEST_ID_PATTERN.matcher(chargeRequestId).matches()))) {
            throw new InvariantViolatedException("Payment: chargeRequestId queda fuera de sus cotas.");
        }
        if (customerRef != null && (customerRef.codePointCount(0, customerRef.length()) < 1 || customerRef.codePointCount(0, customerRef.length()) > 200)) {
            throw new InvariantViolatedException("Payment: customerRef queda fuera de sus cotas.");
        }
        if (amount != null && (amount.compareTo(new BigDecimal("0.001")) < 0)) {
            throw new InvariantViolatedException("Payment: amount queda fuera de sus cotas.");
        }
        if (currency != null && (!CURRENCY_PATTERN.matcher(currency).matches())) {
            throw new InvariantViolatedException("Payment: currency queda fuera de sus cotas.");
        }
        if (gatewayPaymentId != null && (gatewayPaymentId.codePointCount(0, gatewayPaymentId.length()) > 255)) {
            throw new InvariantViolatedException("Payment: gatewayPaymentId queda fuera de sus cotas.");
        }
        // Sus checks (domain: entities.Payment.checks).
        // check 0: awaitingSince es obligatorio cuando status es pending o actionRequired o capturing o canceling o refunding.
        if ((status == PaymentStatus.PENDING || status == PaymentStatus.ACTION_REQUIRED || status == PaymentStatus.CAPTURING || status == PaymentStatus.CANCELING || status == PaymentStatus.REFUNDING) && (awaitingSince == null)) {
            throw new InvariantViolatedException("Payment: awaitingSince es obligatorio cuando status es pending o actionRequired o capturing o canceling o refunding.");
        }
        // check 1: awaitingSince tiene que estar vacío salvo cuando status es pending o actionRequired o capturing o canceling o refunding.
        if (!(status == PaymentStatus.PENDING || status == PaymentStatus.ACTION_REQUIRED || status == PaymentStatus.CAPTURING || status == PaymentStatus.CANCELING || status == PaymentStatus.REFUNDING) && !(awaitingSince == null)) {
            throw new InvariantViolatedException("Payment: awaitingSince tiene que estar vacío salvo cuando status es pending o actionRequired o capturing o canceling o refunding.");
        }
        // check 2: cancelOrigin es obligatorio cuando status es canceling.
        if (status == PaymentStatus.CANCELING && (cancelOrigin == null)) {
            throw new InvariantViolatedException("Payment: cancelOrigin es obligatorio cuando status es canceling.");
        }
        // check 3: cancelOrigin tiene que estar vacío salvo cuando status es canceling.
        if (!(status == PaymentStatus.CANCELING) && !(cancelOrigin == null)) {
            throw new InvariantViolatedException("Payment: cancelOrigin tiene que estar vacío salvo cuando status es canceling.");
        }
        // check 4: failureReason es obligatorio cuando status es failed.
        if (status == PaymentStatus.FAILED && (failureReason == null)) {
            throw new InvariantViolatedException("Payment: failureReason es obligatorio cuando status es failed.");
        }
        // check 5: failureReason tiene que estar vacío salvo cuando status es failed.
        if (!(status == PaymentStatus.FAILED) && !(failureReason == null)) {
            throw new InvariantViolatedException("Payment: failureReason tiene que estar vacío salvo cuando status es failed.");
        }
        // check 6: customerAction es obligatorio cuando status es actionRequired.
        if (status == PaymentStatus.ACTION_REQUIRED && (customerAction == null)) {
            throw new InvariantViolatedException("Payment: customerAction es obligatorio cuando status es actionRequired.");
        }
        // check 7: customerAction es obligatorio cuando cancelOrigin es actionRequired.
        if (cancelOrigin == PaymentCancelOrigin.ACTION_REQUIRED && (customerAction == null)) {
            throw new InvariantViolatedException("Payment: customerAction es obligatorio cuando cancelOrigin es actionRequired.");
        }
        // check 8: customerAction tiene que estar vacío salvo cuando status es actionRequired o canceling.
        if (!(status == PaymentStatus.ACTION_REQUIRED || status == PaymentStatus.CANCELING) && !(customerAction == null)) {
            throw new InvariantViolatedException("Payment: customerAction tiene que estar vacío salvo cuando status es actionRequired o canceling.");
        }
        // check 9: customerAction tiene que estar vacío cuando cancelOrigin es authorized.
        if (cancelOrigin == PaymentCancelOrigin.AUTHORIZED && !(customerAction == null)) {
            throw new InvariantViolatedException("Payment: customerAction tiene que estar vacío cuando cancelOrigin es authorized.");
        }
        // check 10: actionRequiredSince es obligatorio cuando status es actionRequired.
        if (status == PaymentStatus.ACTION_REQUIRED && (actionRequiredSince == null)) {
            throw new InvariantViolatedException("Payment: actionRequiredSince es obligatorio cuando status es actionRequired.");
        }
        // check 11: actionRequiredSince es obligatorio cuando cancelOrigin es actionRequired.
        if (cancelOrigin == PaymentCancelOrigin.ACTION_REQUIRED && (actionRequiredSince == null)) {
            throw new InvariantViolatedException("Payment: actionRequiredSince es obligatorio cuando cancelOrigin es actionRequired.");
        }
        // check 12: actionRequiredSince tiene que estar vacío salvo cuando status es actionRequired o canceling.
        if (!(status == PaymentStatus.ACTION_REQUIRED || status == PaymentStatus.CANCELING) && !(actionRequiredSince == null)) {
            throw new InvariantViolatedException("Payment: actionRequiredSince tiene que estar vacío salvo cuando status es actionRequired o canceling.");
        }
        // check 13: actionRequiredSince tiene que estar vacío cuando cancelOrigin es authorized.
        if (cancelOrigin == PaymentCancelOrigin.AUTHORIZED && !(actionRequiredSince == null)) {
            throw new InvariantViolatedException("Payment: actionRequiredSince tiene que estar vacío cuando cancelOrigin es authorized.");
        }
        // check 14: refundedAmount ≤ amount.
        if (!(Objects.isNull(refundedAmount) || Objects.isNull(amount) || decimalOf(refundedAmount).compareTo(decimalOf(amount)) <= 0)) {
            throw new InvariantViolatedException("Payment: refundedAmount ≤ amount.");
        }
        // check 15: refundedAmount = amount cuando status es refunded.
        if (status == PaymentStatus.REFUNDED && !(Objects.isNull(refundedAmount) || Objects.isNull(amount) || decimalOf(refundedAmount).compareTo(decimalOf(amount)) == 0)) {
            throw new InvariantViolatedException("Payment: refundedAmount = amount cuando status es refunded.");
        }
        // check 16: refundedAmount ≠ amount salvo cuando status es refunded.
        if (!(status == PaymentStatus.REFUNDED) && !(Objects.isNull(refundedAmount) || Objects.isNull(amount) || decimalOf(refundedAmount).compareTo(decimalOf(amount)) != 0)) {
            throw new InvariantViolatedException("Payment: refundedAmount ≠ amount salvo cuando status es refunded.");
        }
    }

    private static BigDecimal decimalOf(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    public UUID getId() {
        return id;
    }

    public String getChargeRequestId() {
        return chargeRequestId;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public Instant getAwaitingSince() {
        return awaitingSince;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public String getCustomerAction() {
        return customerAction;
    }

    public Instant getActionRequiredSince() {
        return actionRequiredSince;
    }

    public PaymentCancelOrigin getCancelOrigin() {
        return cancelOrigin;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** De la devolución más antigua a la más reciente (createdAt y, a igualdad, id). */
    public List<Refund> getRefunds() {
        return refunds.stream()
                .sorted(Comparator.comparing(Refund::getCreatedAt).thenComparing(refund -> refund.getId().toString()))
                .toList();
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
