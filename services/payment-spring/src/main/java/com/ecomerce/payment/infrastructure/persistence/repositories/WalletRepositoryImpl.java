package com.ecomerce.payment.infrastructure.persistence.repositories;

import com.ecomerce.payment.domain.aggregate.Wallet;
import com.ecomerce.payment.domain.entity.PaymentMethod;
import com.ecomerce.payment.domain.history.ChangeHistory;
import com.ecomerce.payment.domain.history.FieldChange;
import com.ecomerce.payment.domain.history.HistoryDiff;
import com.ecomerce.payment.domain.history.HistoryRoots;
import com.ecomerce.payment.domain.repository.WalletRepository;
import com.ecomerce.payment.domain.valueobject.CardDetails;
import com.ecomerce.payment.infrastructure.persistence.entities.PaymentMethodJpa;
import com.ecomerce.payment.infrastructure.persistence.entities.WalletJpa;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Adaptador del puerto WalletRepository.
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
public class WalletRepositoryImpl implements WalletRepository {

    private final WalletJpaRepository walletJpaRepository;
    private final ChangeHistory changeHistory;

    public WalletRepositoryImpl(WalletJpaRepository walletJpaRepository, ChangeHistory changeHistory) {
        this.walletJpaRepository = walletJpaRepository;
        this.changeHistory = changeHistory;
    }

    @Override
    public Optional<Wallet> findById(UUID id) {
        return walletJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Wallet> findByCustomerRef(String customerRef) {
        return walletJpaRepository.findByCustomerRef(customerRef).map(this::toDomain);
    }

    @Override
    @Transactional
    public Wallet save(Wallet entity) {
        // El historial (DSL 2.29, D1 y T1): lo que había, leído ANTES de volcar el agregado, y lo que quedó guardado.
        Map<String, JsonNode> historyBefore = entity.getId() == null ? null : walletJpaRepository.findById(entity.getId()).map(this::toDomain).map(this::historySnapshot).orElse(null);
        Wallet saved = saveUnrecorded(entity);
        recordSave(historyBefore, saved);
        return saved;
    }

    /** El guardado sin el historial: lo que hacía save antes de la 2.29. Solo lo llama save. */
    private Wallet saveUnrecorded(Wallet entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        WalletJpa jpa = entity.getId() != null
                ? walletJpaRepository.findById(entity.getId()).orElseGet(WalletJpa::new)
                : new WalletJpa();
        demoteBeforePromote(entity, jpa);
        applyToJpa(entity, jpa);
        return toDomain(walletJpaRepository.save(jpa));
    }

    /**
     * El índice único parcial «un solo medio por defecto» se comprueba sentencia a sentencia: si el nuevo preferente
     * se escribiese antes de que el anterior dejara de serlo, chocaría contra sí mismo. Lo que deja de ser preferente
     * se escribe primero.
     */
    private void demoteBeforePromote(Wallet domain, WalletJpa jpa) {
        boolean demoted = false;
        for (PaymentMethodJpa child : jpa.getPaymentMethods()) {
            boolean stillDefault = domain.getPaymentMethods().stream()
                    .anyMatch(method -> method.getId().equals(child.getId()) && Boolean.TRUE.equals(method.getIsDefault()));
            if (Boolean.TRUE.equals(child.getIsDefault()) && !stillDefault) {
                child.setIsDefault(false);
                demoted = true;
            }
        }
        if (demoted) {
            walletJpaRepository.flush();
        }
    }

    /** La entrada de un guardado (D2): created si no había nada; updated solo si algo cambió. */
    private void recordSave(Map<String, JsonNode> before, Wallet saved) {
        List<FieldChange> changes = HistoryDiff.diff(before, historySnapshot(saved));
        if (before == null) {
            changeHistory.record(HistoryRoots.WALLET, String.valueOf(saved.getId()), ChangeHistory.CREATED, changes);
        } else if (!changes.isEmpty()) {
            changeHistory.record(HistoryRoots.WALLET, String.valueOf(saved.getId()), ChangeHistory.UPDATED, changes);
        }
    }

    /** Lo que registra el historial de Wallet (DSL 2.29): ruta del contrato → valor del cable. */
    private Map<String, JsonNode> historySnapshot(Wallet entity) {
        Map<String, JsonNode> snapshot = new TreeMap<>();
        snapshot.put("customerRef", changeHistory.value(entity.getCustomerRef()));
        if (entity.getPaymentMethods() != null) {
            for (PaymentMethod child : entity.getPaymentMethods()) {
                String prefix = "paymentMethods[" + child.getId() + "].";
                snapshot.put(prefix + "card.brand", changeHistory.value(HistoryDiff.at(child.getCard(), v -> v.brand())));
                snapshot.put(prefix + "card.last4", changeHistory.value(HistoryDiff.at(child.getCard(), v -> v.last4())));
                snapshot.put(prefix + "card.expMonth", changeHistory.value(HistoryDiff.at(child.getCard(), v -> v.expMonth())));
                snapshot.put(prefix + "card.expYear", changeHistory.value(HistoryDiff.at(child.getCard(), v -> v.expYear())));
                snapshot.put(prefix + "isDefault", changeHistory.value(child.getIsDefault()));
                snapshot.put(prefix + "status", changeHistory.value(child.getStatus()));
                snapshot.put(prefix + "expired", changeHistory.value(child.getExpired()));
            }
        }
        return snapshot;
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        // Una baja deja su entrada (DSL 2.29), solo si había fila.
        if (walletJpaRepository.existsById(id)) {
            walletJpaRepository.deleteById(id);
            changeHistory.record(HistoryRoots.WALLET, String.valueOf(id), ChangeHistory.DELETED, List.of());
        }
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private Wallet toDomain(WalletJpa jpa) {
        return new Wallet(
                jpa.getId(),
                jpa.getCustomerRef(),
                jpa.getPaymentMethods().stream().map(this::toDomain).toList(),
                jpa.getLockVersion());
    }

    private void applyToJpa(Wallet domain, WalletJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setCustomerRef(domain.getCustomerRef());
        Map<UUID, PaymentMethodJpa> paymentMethodsManaged = new HashMap<>();
        for (PaymentMethodJpa child : jpa.getPaymentMethods()) paymentMethodsManaged.put(child.getId(), child);
        List<PaymentMethodJpa> paymentMethodsReconciled = new ArrayList<>();
        for (PaymentMethod child : domain.getPaymentMethods()) {
            PaymentMethodJpa childJpa = child.getId() != null ? paymentMethodsManaged.get(child.getId()) : null;
            if (childJpa == null) childJpa = new PaymentMethodJpa();
            applyToJpa(child, childJpa);
            childJpa.setWallet(jpa);
            paymentMethodsReconciled.add(childJpa);
        }
        jpa.getPaymentMethods().clear();
        jpa.getPaymentMethods().addAll(paymentMethodsReconciled);
        jpa.setLockVersion(domain.getLockVersion());
    }

    private PaymentMethod toDomain(PaymentMethodJpa jpa) {
        return new PaymentMethod(
                jpa.getId(),
                jpa.getGatewayMethodRef(),
                new CardDetails(jpa.getCardBrand(), jpa.getCardLast4(), jpa.getCardExpMonth(), jpa.getCardExpYear()),
                jpa.getIsDefault(),
                jpa.getStatus(),
                jpa.getExpired());
    }

    private void applyToJpa(PaymentMethod domain, PaymentMethodJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setGatewayMethodRef(domain.getGatewayMethodRef());
        jpa.setCardBrand(domain.getCard() != null ? domain.getCard().brand() : null);
        jpa.setCardLast4(domain.getCard() != null ? domain.getCard().last4() : null);
        jpa.setCardExpMonth(domain.getCard() != null ? domain.getCard().expMonth() : null);
        jpa.setCardExpYear(domain.getCard() != null ? domain.getCard().expYear() : null);
        jpa.setIsDefault(domain.getIsDefault());
        jpa.setStatus(domain.getStatus());
        jpa.setExpired(domain.getExpired());
        // wallet: lo estampa WalletRepositoryImpl al mapear su colección.
    }
}
