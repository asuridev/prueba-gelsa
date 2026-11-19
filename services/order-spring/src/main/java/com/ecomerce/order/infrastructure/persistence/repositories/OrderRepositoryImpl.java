package com.ecomerce.order.infrastructure.persistence.repositories;

import com.ecomerce.order.application.support.SweepPass;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.entity.OrderLine;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.parameters.OrderParameters;
import com.ecomerce.order.domain.repository.OrderRepository;
import com.ecomerce.order.domain.repository.ReadScope;
import com.ecomerce.order.infrastructure.persistence.entities.OrderJpa;
import com.ecomerce.order.infrastructure.persistence.entities.OrderLineJpa;
import com.ecomerce.order.infrastructure.persistence.reconciliation.ReconciliationClaimStore;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto OrderRepository.
 *
 * <p>El {@code @Transactional(readOnly = true)} de clase hace que este adaptador se baste solo.
 * Casi siempre es un no-op: los caminos normales (REST, listeners) llegan aquí dentro de la
 * transacción que abrió el {@code UseCaseMediator}, y la propagación por defecto se une a ella
 * ignorando el flag. Importa en el único camino que NO trae transacción: el barrido de
 * reconciliación, que se despacha con {@code dispatchWithoutTransaction} justo para poder colocar
 * sus commits. Sin esto, una consulta que devuelva un agregado con colecciones LAZY reventaría
 * ahí con {@code LazyInitializationException}.
 *
 * <p>Por eso mismo, <b>toda escritura lleva su {@code @Transactional} de método</b>: sin él
 * heredaría el {@code readOnly} de la clase. Si añades una consulta de escritura a este
 * adaptador, anótala — el fallo es en caliente y no silencioso, que es lo que se busca.
 */
@Component
@Transactional(readOnly = true)
public class OrderRepositoryImpl implements OrderRepository {

    private static final Sort TIE_BREAKER = Sort.by(Sort.Order.asc("id"));

    /** Caducidad del reclamo: lo que retiene un candidato la réplica que muera con él en vuelo. */
    @Value("${reconciliation.charge-order.claim-timeout-ms:760000}")
    private long chargeOrderClaimTimeoutMs;
    /** Cota del lote: sin ella, una tanda con 50.000 atascados son 50.000 llamadas al proveedor. */
    @Value("${reconciliation.charge-order.batch-size:50}")
    private int chargeOrderBatchSize;

    /** Caducidad del reclamo: lo que retiene un candidato la réplica que muera con él en vuelo. */
    @Value("${reconciliation.capture-order-payment.claim-timeout-ms:760000}")
    private long captureOrderPaymentClaimTimeoutMs;
    /** Cota del lote: sin ella, una tanda con 50.000 atascados son 50.000 llamadas al proveedor. */
    @Value("${reconciliation.capture-order-payment.batch-size:50}")
    private int captureOrderPaymentBatchSize;

    /** Caducidad del reclamo: lo que retiene un candidato la réplica que muera con él en vuelo. */
    @Value("${reconciliation.cancel-order-payment.claim-timeout-ms:760000}")
    private long cancelOrderPaymentClaimTimeoutMs;
    /** Cota del lote: sin ella, una tanda con 50.000 atascados son 50.000 llamadas al proveedor. */
    @Value("${reconciliation.cancel-order-payment.batch-size:50}")
    private int cancelOrderPaymentBatchSize;

    private final OrderJpaRepository orderJpaRepository;
    private final ReconciliationClaimStore reconciliationClaims;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderParameters orderParameters;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository, ReconciliationClaimStore reconciliationClaims, ApplicationEventPublisher eventPublisher, OrderParameters orderParameters) {
        this.orderParameters = orderParameters;
        this.orderJpaRepository = orderJpaRepository;
        this.reconciliationClaims = reconciliationClaims;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return orderJpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * Añade el id como último criterio de orden si no está ya. Sin desempate, dos
     * páginas consecutivas de la misma consulta pueden repetir una fila y omitir
     * otra: cuando el ORDER BY empata, la base de datos no garantiza un orden
     * estable entre consultas. Se aplica también al orden que pida el cliente,
     * que es justo el caso que un @PageableDefault no cubre.
     */
    private static Pageable withStableOrder(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(TIE_BREAKER));
    }

    @Override
    public Page<Order> listMyOrders(OrderRepository.ListMyOrdersFilter filter, Pageable pageable, ReadScope scope) {
        Specification<OrderJpa> specification = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.customerRef() != null) {
                where.add(cb.equal(root.get("customerRef"), filter.customerRef()));
            }
            if (filter.status() != null) {
                where.add(cb.equal(root.get("status"), filter.status()));
            }
            where.add(readScope(root, query, cb, scope));
            return cb.and(where.toArray(Predicate[]::new));
        };
        return orderJpaRepository.findAll(specification, withStableOrder(pageable)).map(this::toDomain);
    }

    @Override
    public Optional<Order> loadScoped(UUID id, ReadScope scope) {
        return admittedOrMiss(findById(id), scope);
    }

    /** El desenlace de una carga acotada (D5): la fila si el predicado la admite; si no, el readMiss del diseño. */
    private Optional<Order> admittedOrMiss(Optional<Order> found, ReadScope scope) {
        if (found.isEmpty() || scope instanceof ReadScope.Unrestricted) {
            return found;
        }
        UUID key = found.get().getId();
        boolean admitted = orderJpaRepository.exists((root, query, cb) -> cb.and(cb.equal(root.get("id"), key), readScope(root, query, cb, scope)));
        if (admitted) {
            return found;
        }
        // readMiss: not-found. Una fila ajena da el mismo 404 que una que no existe: no revela que existe.
        return Optional.empty();
    }

    /**
     * El alcance de la lectura (security.ownership.Order.reads): Order.customerRef = identidad del llamante. Va en la Specification de cada lectura
     * acotada, así que se suma a sus criterios y llega al conteo de la página (D4). Solo Unrestricted lo ve todo.
     */
    private static Predicate readScope(Root<OrderJpa> root, CriteriaQuery<?> query, CriteriaBuilder cb, ReadScope scope) {
        if (scope instanceof ReadScope.Unrestricted) {
            return cb.conjunction();
        }
        return scoped(cb, root.<String>get("customerRef"), scope);
    }

    /** El campo acotado contra el scope. Un dueño nulo o un conjunto vacío no ve nada (D3): nunca todo. */
    private static Predicate scoped(CriteriaBuilder cb, Expression<String> value, ReadScope scope) {
        if (scope instanceof ReadScope.OwnedBy owned && owned.value() != null) {
            return cb.equal(value, owned.value());
        }
        if (scope instanceof ReadScope.Within within && !within.values().isEmpty()) {
            return value.in(within.values());
        }
        return cb.disjunction();
    }

    /**
     * Reclama los Order que encargaron trabajo a payment.chargeOrder y
     * llevan demasiado tiempo sin desenlace.
     *
     * <p><b>Reclama, no lee.</b> Puede correr a la vez en varias réplicas: con
     * {@code @Scheduled}, en todas; con el cronómetro, cuando un tick se repite al vencer su arriendo. La lista
     * que devuelve son SOLO los candidatos que ESTA instancia se llevó: el reclamo es una
     * marca persistida en {@code reconciliation_claim} que se confirma antes de volver, así
     * que las demás réplicas dejan de verlos. Leerlos con un finder y actuar después haría
     * que las N llamaran al proveedor por el mismo encargo.
     *
     * <p>La marca CADUCA ({@code reconciliation.charge-order.claim-timeout-ms}): sobrevive
     * al commit, así que también sobreviviría a la réplica que muera con el candidato en
     * vuelo. Dimensiona el plazo por encima de lote × timeout de llamada.
     *
     * <p>No recibe el tamaño del lote: los tres números —umbral, caducidad y cota— salen de
     * {@code parameters/<perfil>/reconciliation.yaml}, y quien los lee es el adaptador.
     * Actúa sobre lo que devuelve FUERA de esta llamada.
     */
    @Override
    @Transactional
    public List<Order> claimForReconcileStalledOrdersChargeOrder() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(java.time.Duration.ofMinutes(orderParameters.paymentPendingTimeoutMinutes()));
        Instant actionRequiredStaleBefore = now.minus(java.time.Duration.ofHours(orderParameters.actionRequiredTimeoutHours()));
        Instant claimExpiredBefore = now.minusMillis(chargeOrderClaimTimeoutMs);
        List<UUID> candidates = orderJpaRepository.candidatesForReconcileStalledOrdersChargeOrder(
                OrderStatus.PENDING_PAYMENT, staleBefore, OrderStatus.ACTION_REQUIRED, actionRequiredStaleBefore, "chargeOrder", claimExpiredBefore, PageRequest.of(0, chargeOrderBatchSize));
        List<Order> claimed = new ArrayList<>();
        for (UUID id : candidates) {
            // true = la marca es mía; false = otra réplica la tiene y aún no ha caducado.
            if (reconciliationClaims.claim("chargeOrder", id, now, claimExpiredBefore)) {
                orderJpaRepository.findById(id).map(this::toDomain).ifPresent(claimed::add);
            }
        }
        // Lote lleno: puede que queden candidatos, y el tick del cronómetro repite la pasada (drenaje).
        SweepPass.claimed(candidates.size(), chargeOrderBatchSize);
        return claimed;
    }

    /**
     * Reclama los Order que encargaron trabajo a payment.captureOrderPayment y
     * llevan demasiado tiempo sin desenlace.
     *
     * <p><b>Reclama, no lee.</b> Puede correr a la vez en varias réplicas: con
     * {@code @Scheduled}, en todas; con el cronómetro, cuando un tick se repite al vencer su arriendo. La lista
     * que devuelve son SOLO los candidatos que ESTA instancia se llevó: el reclamo es una
     * marca persistida en {@code reconciliation_claim} que se confirma antes de volver, así
     * que las demás réplicas dejan de verlos. Leerlos con un finder y actuar después haría
     * que las N llamaran al proveedor por el mismo encargo.
     *
     * <p>La marca CADUCA ({@code reconciliation.capture-order-payment.claim-timeout-ms}): sobrevive
     * al commit, así que también sobreviviría a la réplica que muera con el candidato en
     * vuelo. Dimensiona el plazo por encima de lote × timeout de llamada.
     *
     * <p>No recibe el tamaño del lote: los tres números —umbral, caducidad y cota— salen de
     * {@code parameters/<perfil>/reconciliation.yaml}, y quien los lee es el adaptador.
     * Actúa sobre lo que devuelve FUERA de esta llamada.
     */
    @Override
    @Transactional
    public List<Order> claimForReconcileStalledOrdersCaptureOrderPayment() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(java.time.Duration.ofMinutes(orderParameters.paymentActionRetryMinutes()));
        Instant claimExpiredBefore = now.minusMillis(captureOrderPaymentClaimTimeoutMs);
        List<OrderStatus> states = List.of(OrderStatus.AUTHORIZED);
        List<UUID> candidates = orderJpaRepository.candidatesForReconcileStalledOrdersCaptureOrderPayment(
                states, staleBefore, "captureOrderPayment", claimExpiredBefore, PageRequest.of(0, captureOrderPaymentBatchSize));
        List<Order> claimed = new ArrayList<>();
        for (UUID id : candidates) {
            // true = la marca es mía; false = otra réplica la tiene y aún no ha caducado.
            if (reconciliationClaims.claim("captureOrderPayment", id, now, claimExpiredBefore)) {
                orderJpaRepository.findById(id).map(this::toDomain).ifPresent(claimed::add);
            }
        }
        // Lote lleno: puede que queden candidatos, y el tick del cronómetro repite la pasada (drenaje).
        SweepPass.claimed(candidates.size(), captureOrderPaymentBatchSize);
        return claimed;
    }

    /**
     * Reclama los Order que encargaron trabajo a payment.cancelOrderPayment y
     * llevan demasiado tiempo sin desenlace.
     *
     * <p><b>Reclama, no lee.</b> Puede correr a la vez en varias réplicas: con
     * {@code @Scheduled}, en todas; con el cronómetro, cuando un tick se repite al vencer su arriendo. La lista
     * que devuelve son SOLO los candidatos que ESTA instancia se llevó: el reclamo es una
     * marca persistida en {@code reconciliation_claim} que se confirma antes de volver, así
     * que las demás réplicas dejan de verlos. Leerlos con un finder y actuar después haría
     * que las N llamaran al proveedor por el mismo encargo.
     *
     * <p>La marca CADUCA ({@code reconciliation.cancel-order-payment.claim-timeout-ms}): sobrevive
     * al commit, así que también sobreviviría a la réplica que muera con el candidato en
     * vuelo. Dimensiona el plazo por encima de lote × timeout de llamada.
     *
     * <p>No recibe el tamaño del lote: los tres números —umbral, caducidad y cota— salen de
     * {@code parameters/<perfil>/reconciliation.yaml}, y quien los lee es el adaptador.
     * Actúa sobre lo que devuelve FUERA de esta llamada.
     */
    @Override
    @Transactional
    public List<Order> claimForReconcileStalledOrdersCancelOrderPayment() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(java.time.Duration.ofMinutes(orderParameters.paymentActionRetryMinutes()));
        Instant claimExpiredBefore = now.minusMillis(cancelOrderPaymentClaimTimeoutMs);
        List<OrderStatus> states = List.of(OrderStatus.CANCELING);
        List<UUID> candidates = orderJpaRepository.candidatesForReconcileStalledOrdersCancelOrderPayment(
                states, staleBefore, "cancelOrderPayment", claimExpiredBefore, PageRequest.of(0, cancelOrderPaymentBatchSize));
        List<Order> claimed = new ArrayList<>();
        for (UUID id : candidates) {
            // true = la marca es mía; false = otra réplica la tiene y aún no ha caducado.
            if (reconciliationClaims.claim("cancelOrderPayment", id, now, claimExpiredBefore)) {
                orderJpaRepository.findById(id).map(this::toDomain).ifPresent(claimed::add);
            }
        }
        // Lote lleno: puede que queden candidatos, y el tick del cronómetro repite la pasada (drenaje).
        SweepPass.claimed(candidates.size(), cancelOrderPaymentBatchSize);
        return claimed;
    }

    @Override
    @Transactional
    public Order save(Order entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        OrderJpa jpa = entity.getId() != null
                ? orderJpaRepository.findById(entity.getId()).orElseGet(OrderJpa::new)
                : new OrderJpa();
        applyToJpa(entity, jpa);
        Order saved = toDomain(orderJpaRepository.saveAndFlush(jpa));
        entity.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        orderJpaRepository.deleteById(id);
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private Order toDomain(OrderJpa jpa) {
        return new Order(
                jpa.getId(),
                jpa.getCustomerRef(),
                jpa.getCustomerEmail(),
                jpa.getStatus(),
                jpa.getCurrency(),
                jpa.getTotalAmount(),
                jpa.getLineCount(),
                jpa.getPaymentId(),
                jpa.getCustomerAction(),
                jpa.getFailureReason(),
                jpa.getChargeOrderAwaitingSince(),
                jpa.getCaptureOrderPaymentAwaitingSince(),
                jpa.getActionRequiredSince(),
                jpa.getCancelOrderPaymentAwaitingSince(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt(),
                jpa.getLines().stream().map(this::toDomain).toList(),
                jpa.getLockVersion());
    }

    private void applyToJpa(Order domain, OrderJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setCustomerRef(domain.getCustomerRef());
        jpa.setCustomerEmail(domain.getCustomerEmail());
        jpa.setStatus(domain.getStatus());
        jpa.setCurrency(domain.getCurrency());
        jpa.setTotalAmount(domain.getTotalAmount());
        jpa.setLineCount(domain.getLineCount());
        jpa.setPaymentId(domain.getPaymentId());
        jpa.setCustomerAction(domain.getCustomerAction());
        jpa.setFailureReason(domain.getFailureReason());
        jpa.setChargeOrderAwaitingSince(domain.getChargeOrderAwaitingSince());
        jpa.setCaptureOrderPaymentAwaitingSince(domain.getCaptureOrderPaymentAwaitingSince());
        jpa.setActionRequiredSince(domain.getActionRequiredSince());
        jpa.setCancelOrderPaymentAwaitingSince(domain.getCancelOrderPaymentAwaitingSince());
        jpa.setCreatedAt(domain.getCreatedAt());
        jpa.setUpdatedAt(domain.getUpdatedAt());
        Map<UUID, OrderLineJpa> linesManaged = new HashMap<>();
        for (OrderLineJpa child : jpa.getLines()) linesManaged.put(child.getId(), child);
        List<OrderLineJpa> linesReconciled = new ArrayList<>();
        for (OrderLine child : domain.getLines()) {
            OrderLineJpa childJpa = child.getId() != null ? linesManaged.get(child.getId()) : null;
            if (childJpa == null) childJpa = new OrderLineJpa();
            applyToJpa(child, childJpa);
            linesReconciled.add(childJpa);
        }
        jpa.getLines().clear();
        jpa.getLines().addAll(linesReconciled);
        jpa.setLockVersion(domain.getLockVersion());
    }

    private OrderLine toDomain(OrderLineJpa jpa) {
        return new OrderLine(
                jpa.getId(),
                jpa.getProductId(),
                jpa.getSku(),
                jpa.getProductName(),
                jpa.getUnitPrice(),
                jpa.getQuantity());
    }

    private void applyToJpa(OrderLine domain, OrderLineJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setProductId(domain.getProductId());
        jpa.setSku(domain.getSku());
        jpa.setProductName(domain.getProductName());
        jpa.setUnitPrice(domain.getUnitPrice());
        jpa.setQuantity(domain.getQuantity());
    }
}
