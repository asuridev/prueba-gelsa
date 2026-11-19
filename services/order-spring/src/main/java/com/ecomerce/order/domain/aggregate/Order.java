package com.ecomerce.order.domain.aggregate;

import com.ecomerce.order.domain.entity.OrderLine;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.errors.DuplicateOrderLineError;
import com.ecomerce.order.domain.errors.InvalidStateTransitionException;
import com.ecomerce.order.domain.errors.InvariantViolatedException;
import com.ecomerce.order.domain.events.DomainEvent;
import com.ecomerce.order.domain.events.NotificationRequestedEvent;
import com.ecomerce.order.domain.identity.Uuids;
import com.ecomerce.order.domain.valueobject.CurrencyCodeFormat;
import com.ecomerce.order.domain.valueobject.EmailAddressFormat;
import com.ecomerce.order.domain.valueobject.NotificationVariable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un pedido de un comprador: sus líneas con el precio congelado al crearlo y el seguimiento del cobro que se pidió a payment por su importe total.
 */
public class Order {

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

    // Id del pedido; es también el chargeRequestId del cobro en payment.
    private UUID id = Uuids.v7();

    // Comprador titular del pedido (sub de su token).
    private String customerRef;

    // Dirección a la que se notifica el resultado del cobro; sale del claim email del token.
    private String customerEmail;

    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    // Moneda del pedido; la comparten todos sus productos.
    private String currency;

    // Importe a cobrar; suma de precio unitario por cantidad de cada línea.
    private BigDecimal totalAmount;

    private Integer lineCount;

    // Id del cobro en payment; vacío mientras payment no lo ha registrado.
    private UUID paymentId;

    // Acción opaca de autenticación (3DS) que el navegador entrega al componente de la pasarela; solo en actionRequired.
    private String customerAction;

    // Por qué falló el cobro; solo en paymentFailed.
    private PaymentFailureReason failureReason;

    // Cuándo se pidió el cobro a payment; lo estampa createOrder y lo renueva el barrido cuando payment responde pending. Es el reloj del barrido de pedidos en pendingPayment.
    private Instant chargeOrderAwaitingSince;

    // Cuándo se pidió (o se esperó) la captura por última vez; lo estampa TODA operación que pasa el pedido a authorized (createOrder, cancelOrder, applyPaymentAuthorized, applyPaymentActionRejected) y el reintento del barrido. Es el reloj del barrido de pedidos en authorized.
    private Instant captureOrderPaymentAwaitingSince;

    // Cuándo entró el pedido en actionRequired; lo estampan createOrder y applyPaymentActionRequired. Es el reloj del barrido que rescata un 3DS cuyo desenlace no llegó.
    private Instant actionRequiredSince;

    // Cuándo se pidió la anulación por última vez; lo estampan cancelOrder y el reintento del barrido. Es el reloj del barrido de pedidos en canceling.
    private Instant cancelOrderPaymentAwaitingSince;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    private List<OrderLine> lines = new ArrayList<>();

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /**
     * Factory de creación: un pedido nace en pendingPayment con el reloj del cobro en marcha
     * (chargeOrderAwaitingSince) y con sus líneas ya congeladas. Aplica las invariantes del
     * agregado: al menos una línea y un mismo producto como mucho en una línea.
     */
    public static Order create(UUID id, String customerRef, String customerEmail, String currency, List<OrderLine> lines) {
        EmailAddressFormat.validate(customerEmail);
        CurrencyCodeFormat.validate(currency);
        if (lines == null || lines.isEmpty()) {
            throw new InvariantViolatedException("Order: un pedido necesita al menos una línea.");
        }
        Set<UUID> seen = new HashSet<>();
        for (OrderLine line : lines) {
            if (!seen.add(line.getProductId())) {
                throw new DuplicateOrderLineError("El producto " + line.getProductId() + " aparece en más de una línea");
            }
        }
        Instant now = Instant.now();
        return new Order(id != null ? id : Uuids.v7(), customerRef, customerEmail, OrderStatus.PENDING_PAYMENT, currency,
                null, null, null, null, null, now, null, null, null, now, now, lines, null);
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public Order(UUID id, String customerRef, String customerEmail, OrderStatus status, String currency, BigDecimal totalAmount, Integer lineCount, UUID paymentId, String customerAction, PaymentFailureReason failureReason, Instant chargeOrderAwaitingSince, Instant captureOrderPaymentAwaitingSince, Instant actionRequiredSince, Instant cancelOrderPaymentAwaitingSince, Instant createdAt, Instant updatedAt, List<OrderLine> lines, Long lockVersion) {
        this.id = id;
        this.customerRef = customerRef;
        this.customerEmail = customerEmail;
        this.status = status;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.lineCount = lineCount;
        this.paymentId = paymentId;
        this.customerAction = customerAction;
        this.failureReason = failureReason;
        this.chargeOrderAwaitingSince = chargeOrderAwaitingSince;
        this.captureOrderPaymentAwaitingSince = captureOrderPaymentAwaitingSince;
        this.actionRequiredSince = actionRequiredSince;
        this.cancelOrderPaymentAwaitingSince = cancelOrderPaymentAwaitingSince;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lines = new ArrayList<>(lines);
        this.lockVersion = lockVersion;
    }

    // Transiciones válidas del lifecycle del diseño; un estado con Set.of() es terminal.
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.ACTION_REQUIRED, OrderStatus.AUTHORIZED, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELED),
        OrderStatus.ACTION_REQUIRED, Set.of(OrderStatus.AUTHORIZED, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELING, OrderStatus.CANCELED),
        OrderStatus.AUTHORIZED, Set.of(OrderStatus.PAID, OrderStatus.CANCELED),
        OrderStatus.CANCELING, Set.of(OrderStatus.CANCELED, OrderStatus.ACTION_REQUIRED, OrderStatus.AUTHORIZED, OrderStatus.PAYMENT_FAILED),
        OrderStatus.PAID, Set.of(),
        OrderStatus.CANCELED, Set.of(),
        OrderStatus.PAYMENT_FAILED, Set.of()
    );

    // ─── Métodos semánticos del lifecycle ────────────────────────────────────
    // Cada uno valida el estado de origen con transitionTo (InvalidStateTransitionException si no
    // procede) y deja el agregado coherente con los checks del diseño.

    /** ¿Está el pedido en alguno de estos estados? Lo usan los handlers para decidir si una operación procede o es un no-op. */
    public boolean isIn(OrderStatus... states) {
        for (OrderStatus candidate : states) {
            if (status == candidate) {
                return true;
            }
        }
        return false;
    }

    /** Guarda el id del cobro que payment registró, sin mover el estado (pendingPayment con cobro pending). */
    public void registerPayment(UUID paymentId) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException(status.name(), OrderStatus.PENDING_PAYMENT.name());
        }
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        touch();
    }

    /**
     * pendingPayment | actionRequired | canceling → authorized. Guarda el cobro, borra la acción
     * de autenticación y estampa el reloj de la captura.
     */
    public void authorize(UUID paymentId) {
        transitionTo(OrderStatus.AUTHORIZED);
        if (paymentId != null) {
            this.paymentId = paymentId;
        }
        this.customerAction = null;
        this.captureOrderPaymentAwaitingSince = Instant.now();
        touch();
    }

    /** pendingPayment → actionRequired: el comprador tiene que completar el 3DS. */
    public void requireCustomerAction(UUID paymentId, String customerAction) {
        transitionTo(OrderStatus.ACTION_REQUIRED);
        this.paymentId = paymentId;
        this.customerAction = customerAction;
        this.actionRequiredSince = Instant.now();
        touch();
    }

    /** pendingPayment | actionRequired | canceling → paymentFailed, y pide el correo de pago fallido. */
    public void failPayment(UUID paymentId, PaymentFailureReason reason) {
        transitionTo(OrderStatus.PAYMENT_FAILED);
        if (paymentId != null) {
            this.paymentId = paymentId;
        }
        this.customerAction = null;
        this.failureReason = Objects.requireNonNull(reason, "reason");
        touch();
        requestNotification("order-payment-failed", "payment-failed", true);
    }

    /** pendingPayment | actionRequired | authorized | canceling → canceled, y pide el correo de cancelación. */
    public void markCanceled(UUID paymentId) {
        transitionTo(OrderStatus.CANCELED);
        if (paymentId != null) {
            this.paymentId = paymentId;
        }
        this.customerAction = null;
        touch();
        requestNotification("order-canceled", "canceled", false);
    }

    /** authorized → paid, y pide el correo de pago confirmado. */
    public void markPaid() {
        transitionTo(OrderStatus.PAID);
        touch();
        requestNotification("order-paid", "paid", false);
    }

    /** actionRequired → canceling: la anulación se pide a payment y se espera su desenlace. */
    public void startCancellation() {
        transitionTo(OrderStatus.CANCELING);
        this.cancelOrderPaymentAwaitingSince = Instant.now();
        touch();
    }

    /** canceling → actionRequired: payment no pudo anular; conserva la acción de autenticación. */
    public void resumeCustomerAction() {
        transitionTo(OrderStatus.ACTION_REQUIRED);
        touch();
    }

    /** Renueva el reloj del barrido de pendingPayment (payment respondió pending). */
    public void renewChargeAwaiting() {
        this.chargeOrderAwaitingSince = Instant.now();
        touch();
    }

    /** Renueva el reloj del barrido de authorized (se volvió a pedir la captura). */
    public void renewCaptureAwaiting() {
        this.captureOrderPaymentAwaitingSince = Instant.now();
        touch();
    }

    /** Renueva el reloj del barrido de canceling (se volvió a pedir la anulación). */
    public void renewCancelAwaiting() {
        this.cancelOrderPaymentAwaitingSince = Instant.now();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Encarga a notifications el correo del desenlace. La clave de idempotencia deriva del pedido y
     * del desenlace; totalAmount va como texto con los decimales de la moneda (ISO 4217).
     */
    private void requestNotification(String templateCode, String outcome, boolean withFailureReason) {
        List<NotificationVariable> variables = new ArrayList<>();
        variables.add(new NotificationVariable("orderId", id.toString()));
        variables.add(new NotificationVariable("totalAmount", formatAmount(computeTotalAmount(), currency)));
        variables.add(new NotificationVariable("currency", currency));
        if (withFailureReason && failureReason != null) {
            variables.add(new NotificationVariable("failureReason", failureReason.getValue()));
        }
        raise(NotificationRequestedEvent.of("order:" + id + ":" + outcome, templateCode, List.of(customerEmail), variables));
    }

    private static String formatAmount(BigDecimal amount, String currencyCode) {
        int digits;
        try {
            digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        } catch (IllegalArgumentException unknown) {
            digits = -1;
        }
        BigDecimal scaled = digits >= 0 ? amount.setScale(digits, RoundingMode.HALF_UP) : amount.stripTrailingZeros();
        return scaled.toPlainString();
    }

    // Guard interno del lifecycle: lo llaman los métodos semánticos, nunca un handler.
    private void transitionTo(OrderStatus target) {
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(status.name(), target.name());
        }
        this.status = target;
    }

    private static final Pattern CUSTOMER_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        for (OrderLine child : lines) {
            child.ensureInvariants();
        }
        if (exceedsDecimal(computeTotalAmount(), 3)) {
            throw new InvariantViolatedException("Order: totalAmount no cabe en su tipo.");
        }
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (customerRef != null && (customerRef.codePointCount(0, customerRef.length()) < 1 || customerRef.codePointCount(0, customerRef.length()) > 200)) {
            throw new InvariantViolatedException("Order: customerRef queda fuera de sus cotas.");
        }
        if (customerEmail != null && ((customerEmail.codePointCount(0, customerEmail.length()) > 254) || (!CUSTOMER_EMAIL_PATTERN.matcher(customerEmail).matches()))) {
            throw new InvariantViolatedException("Order: customerEmail queda fuera de sus cotas.");
        }
        if (currency != null && (!CURRENCY_PATTERN.matcher(currency).matches())) {
            throw new InvariantViolatedException("Order: currency queda fuera de sus cotas.");
        }
        if (totalAmount != null && (totalAmount.compareTo(new BigDecimal("0")) < 0)) {
            throw new InvariantViolatedException("Order: totalAmount queda fuera de sus cotas.");
        }
        // Sus checks (domain: entities.Order.checks).
        // check 0: customerAction es obligatorio cuando status es actionRequired.
        if (status == OrderStatus.ACTION_REQUIRED && (customerAction == null)) {
            throw new InvariantViolatedException("Order: customerAction es obligatorio cuando status es actionRequired.");
        }
        // check 1: customerAction tiene que estar vacío salvo cuando status es actionRequired o canceling.
        if (!(status == OrderStatus.ACTION_REQUIRED || status == OrderStatus.CANCELING) && !(customerAction == null)) {
            throw new InvariantViolatedException("Order: customerAction tiene que estar vacío salvo cuando status es actionRequired o canceling.");
        }
        // check 2: failureReason es obligatorio cuando status es paymentFailed.
        if (status == OrderStatus.PAYMENT_FAILED && (failureReason == null)) {
            throw new InvariantViolatedException("Order: failureReason es obligatorio cuando status es paymentFailed.");
        }
        // check 3: failureReason tiene que estar vacío salvo cuando status es paymentFailed.
        if (!(status == OrderStatus.PAYMENT_FAILED) && !(failureReason == null)) {
            throw new InvariantViolatedException("Order: failureReason tiene que estar vacío salvo cuando status es paymentFailed.");
        }
        // check 4: paymentId es obligatorio cuando status es actionRequired o authorized o paid o canceling.
        if ((status == OrderStatus.ACTION_REQUIRED || status == OrderStatus.AUTHORIZED || status == OrderStatus.PAID || status == OrderStatus.CANCELING) && (paymentId == null)) {
            throw new InvariantViolatedException("Order: paymentId es obligatorio cuando status es actionRequired o authorized o paid o canceling.");
        }
        // check 5: totalAmount > 0.
        if (!(Objects.isNull(totalAmount) || decimalOf(totalAmount).compareTo(new BigDecimal("0")) > 0)) {
            throw new InvariantViolatedException("Order: totalAmount > 0.");
        }
    }

    // Derivado (DSL 2.31): sum de lines.unitPrice × quantity. Exacto y un solo redondeo,
    // HALF_UP, a la escala del campo; una hija con el sumando o el factor ausente aporta cero.
    private BigDecimal computeTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine item : lines) {
            if (item.getUnitPrice() == null || item.getQuantity() == null) {
                continue;
            }
            BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(line);
        }
        return total.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimalOf(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    // Un decimal desborda cuando su parte entera no cabe en la columna: 19 dígitos menos la escala.
    private static boolean exceedsDecimal(BigDecimal value, int scale) {
        return value.precision() - value.scale() > 19 - scale;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    // Lo calcula la entidad y lo persiste el save (ensureInvariants() ya comprobó que cabe); nunca viene del cliente.
    public BigDecimal getTotalAmount() {
        return computeTotalAmount();
    }

    // Derivado (DSL 2.31): count de lines. Lo calcula la entidad y lo persiste el save; nunca viene del cliente.
    public Integer getLineCount() {
        return lines.size();
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getCustomerAction() {
        return customerAction;
    }

    public PaymentFailureReason getFailureReason() {
        return failureReason;
    }

    public Instant getChargeOrderAwaitingSince() {
        return chargeOrderAwaitingSince;
    }

    public Instant getCaptureOrderPaymentAwaitingSince() {
        return captureOrderPaymentAwaitingSince;
    }

    public Instant getActionRequiredSince() {
        return actionRequiredSince;
    }

    public Instant getCancelOrderPaymentAwaitingSince() {
        return cancelOrderPaymentAwaitingSince;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderLine> getLines() {
        return List.copyOf(lines);
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
