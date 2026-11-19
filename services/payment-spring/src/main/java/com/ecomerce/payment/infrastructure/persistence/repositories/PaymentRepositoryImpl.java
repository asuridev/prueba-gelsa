package com.ecomerce.payment.infrastructure.persistence.repositories;

import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.entity.Refund;
import com.ecomerce.payment.domain.history.ChangeHistory;
import com.ecomerce.payment.domain.history.FieldChange;
import com.ecomerce.payment.domain.history.HistoryDiff;
import com.ecomerce.payment.domain.history.HistoryRoots;
import com.ecomerce.payment.domain.repository.PaymentRepository;
import com.ecomerce.payment.infrastructure.persistence.entities.PaymentJpa;
import com.ecomerce.payment.infrastructure.persistence.entities.RefundJpa;
import jakarta.persistence.criteria.Predicate;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Adaptador del puerto PaymentRepository.
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
public class PaymentRepositoryImpl implements PaymentRepository {

    private static final Sort TIE_BREAKER = Sort.by(Sort.Order.asc("id"));

    private final PaymentJpaRepository paymentJpaRepository;
    private final ChangeHistory changeHistory;
    private final ApplicationEventPublisher eventPublisher;

    /** Estados que esperan un desenlace de la pasarela: los candidatos del barrido. */
    private static final List<PaymentStatus> AWAITING = List.of(PaymentStatus.PENDING, PaymentStatus.ACTION_REQUIRED,
            PaymentStatus.CAPTURING, PaymentStatus.CANCELING, PaymentStatus.REFUNDING);

    private final TransactionTemplate registration;

    public PaymentRepositoryImpl(PaymentJpaRepository paymentJpaRepository, ChangeHistory changeHistory, ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.paymentJpaRepository = paymentJpaRepository;
        this.changeHistory = changeHistory;
        this.eventPublisher = eventPublisher;
        this.registration = new TransactionTemplate(transactionManager);
        this.registration.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * El registro de un cobro nuevo, en su propia transacción: la violación de la clave natural sale de ELLA,
     * ya revertida, y se traduce a «ya existía» sin dejar marcada para revertir ninguna transacción ajena.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<Payment> register(Payment entity) {
        try {
            return Optional.of(registration.execute(status -> save(entity)));
        } catch (DataIntegrityViolationException collision) {
            if (paymentJpaRepository.findByChargeRequestId(entity.getChargeRequestId()).isPresent()) {
                return Optional.empty();
            }
            throw collision;
        }
    }

    @Override
    public List<Payment> findAwaitingBefore(Instant staleBefore, int limit) {
        return paymentJpaRepository.findAwaiting(AWAITING, staleBefore, PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean claimAwaiting(UUID id, Instant expected, Instant renewed) {
        return paymentJpaRepository.claimAwaiting(id, expected, renewed) == 1;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return paymentJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByChargeRequestId(String chargeRequestId) {
        return paymentJpaRepository.findByChargeRequestId(chargeRequestId).map(this::toDomain);
    }

    @Override
    public Page<Payment> list(Pageable pageable) {
        return paymentJpaRepository.findAll(withStableOrder(pageable)).map(this::toDomain);
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
    public Page<Payment> listPaymentsByCustomer(PaymentRepository.ListPaymentsByCustomerFilter filter, Pageable pageable) {
        Specification<PaymentJpa> specification = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.customerRef() != null) {
                where.add(cb.equal(root.get("customerRef"), filter.customerRef()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return paymentJpaRepository.findAll(specification, withStableOrder(pageable)).map(this::toDomain);
    }

    @Override
    @Transactional
    public Payment save(Payment entity) {
        // El historial (DSL 2.29, D1 y T1): lo que había, leído ANTES de volcar el agregado, y lo que quedó guardado.
        Map<String, JsonNode> historyBefore = entity.getId() == null ? null : paymentJpaRepository.findById(entity.getId()).map(this::toDomain).map(this::historySnapshot).orElse(null);
        Payment saved = saveUnrecorded(entity);
        recordSave(historyBefore, saved);
        return saved;
    }

    /** El guardado sin el historial: lo que hacía save antes de la 2.29. Solo lo llama save. */
    private Payment saveUnrecorded(Payment entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        PaymentJpa jpa = entity.getId() != null
                ? paymentJpaRepository.findById(entity.getId()).orElseGet(PaymentJpa::new)
                : new PaymentJpa();
        applyToJpa(entity, jpa);
        Payment saved = toDomain(paymentJpaRepository.saveAndFlush(jpa));
        entity.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }

    /** La entrada de un guardado (D2): created si no había nada; updated solo si algo cambió. */
    private void recordSave(Map<String, JsonNode> before, Payment saved) {
        List<FieldChange> changes = HistoryDiff.diff(before, historySnapshot(saved));
        if (before == null) {
            changeHistory.record(HistoryRoots.PAYMENT, String.valueOf(saved.getId()), ChangeHistory.CREATED, changes);
        } else if (!changes.isEmpty()) {
            changeHistory.record(HistoryRoots.PAYMENT, String.valueOf(saved.getId()), ChangeHistory.UPDATED, changes);
        }
    }

    /** Lo que registra el historial de Payment (DSL 2.29): ruta del contrato → valor del cable. */
    private Map<String, JsonNode> historySnapshot(Payment entity) {
        Map<String, JsonNode> snapshot = new TreeMap<>();
        snapshot.put("chargeRequestId", changeHistory.value(entity.getChargeRequestId()));
        snapshot.put("customerRef", changeHistory.value(entity.getCustomerRef()));
        snapshot.put("amount", changeHistory.value(entity.getAmount()));
        snapshot.put("currency", changeHistory.value(entity.getCurrency()));
        snapshot.put("status", changeHistory.value(entity.getStatus()));
        snapshot.put("gatewayPaymentId", changeHistory.value(entity.getGatewayPaymentId()));
        snapshot.put("awaitingSince", changeHistory.value(entity.getAwaitingSince()));
        snapshot.put("failureReason", changeHistory.value(entity.getFailureReason()));
        snapshot.put("customerAction", changeHistory.value(entity.getCustomerAction()));
        snapshot.put("actionRequiredSince", changeHistory.value(entity.getActionRequiredSince()));
        snapshot.put("cancelOrigin", changeHistory.value(entity.getCancelOrigin()));
        snapshot.put("paymentMethodId", changeHistory.value(entity.getPaymentMethodId()));
        snapshot.put("refundedAmount", changeHistory.value(entity.getRefundedAmount()));
        if (entity.getRefunds() != null) {
            for (Refund child : entity.getRefunds()) {
                String prefix = "refunds[" + child.getId() + "].";
                snapshot.put(prefix + "refundRequestId", changeHistory.value(child.getRefundRequestId()));
                snapshot.put(prefix + "amount", changeHistory.value(child.getAmount()));
                snapshot.put(prefix + "reason", changeHistory.value(child.getReason()));
                snapshot.put(prefix + "status", changeHistory.value(child.getStatus()));
            }
        }
        return snapshot;
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        // Una baja deja su entrada (DSL 2.29), solo si había fila.
        if (paymentJpaRepository.existsById(id)) {
            paymentJpaRepository.deleteById(id);
            changeHistory.record(HistoryRoots.PAYMENT, String.valueOf(id), ChangeHistory.DELETED, List.of());
        }
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private Payment toDomain(PaymentJpa jpa) {
        return new Payment(
                jpa.getId(),
                jpa.getChargeRequestId(),
                jpa.getCustomerRef(),
                jpa.getAmount(),
                jpa.getCurrency(),
                jpa.getStatus(),
                jpa.getGatewayPaymentId(),
                jpa.getAwaitingSince(),
                jpa.getFailureReason(),
                jpa.getCustomerAction(),
                jpa.getActionRequiredSince(),
                jpa.getCancelOrigin(),
                jpa.getPaymentMethodId(),
                jpa.getRefundedAmount(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt(),
                jpa.getRefunds().stream().map(this::toDomain).toList(),
                jpa.getLockVersion());
    }

    private void applyToJpa(Payment domain, PaymentJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setChargeRequestId(domain.getChargeRequestId());
        jpa.setCustomerRef(domain.getCustomerRef());
        jpa.setAmount(domain.getAmount());
        jpa.setCurrency(domain.getCurrency());
        jpa.setStatus(domain.getStatus());
        jpa.setGatewayPaymentId(domain.getGatewayPaymentId());
        jpa.setAwaitingSince(domain.getAwaitingSince());
        jpa.setFailureReason(domain.getFailureReason());
        jpa.setCustomerAction(domain.getCustomerAction());
        jpa.setActionRequiredSince(domain.getActionRequiredSince());
        jpa.setCancelOrigin(domain.getCancelOrigin());
        jpa.setPaymentMethodId(domain.getPaymentMethodId());
        jpa.setRefundedAmount(domain.getRefundedAmount());
        jpa.setCreatedAt(domain.getCreatedAt());
        jpa.setUpdatedAt(domain.getUpdatedAt());
        Map<UUID, RefundJpa> refundsManaged = new HashMap<>();
        for (RefundJpa child : jpa.getRefunds()) refundsManaged.put(child.getId(), child);
        List<RefundJpa> refundsReconciled = new ArrayList<>();
        for (Refund child : domain.getRefunds()) {
            RefundJpa childJpa = child.getId() != null ? refundsManaged.get(child.getId()) : null;
            if (childJpa == null) childJpa = new RefundJpa();
            applyToJpa(child, childJpa);
            childJpa.setPayment(jpa);
            refundsReconciled.add(childJpa);
        }
        jpa.getRefunds().clear();
        jpa.getRefunds().addAll(refundsReconciled);
        jpa.setLockVersion(domain.getLockVersion());
    }

    private Refund toDomain(RefundJpa jpa) {
        return new Refund(
                jpa.getId(),
                jpa.getRefundRequestId(),
                jpa.getAmount(),
                jpa.getReason(),
                jpa.getStatus(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt());
    }

    private void applyToJpa(Refund domain, RefundJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setRefundRequestId(domain.getRefundRequestId());
        jpa.setAmount(domain.getAmount());
        jpa.setReason(domain.getReason());
        jpa.setStatus(domain.getStatus());
        jpa.setCreatedAt(domain.getCreatedAt());
        jpa.setUpdatedAt(domain.getUpdatedAt());
        // payment: lo estampa PaymentRepositoryImpl al mapear su colección.
    }
}
