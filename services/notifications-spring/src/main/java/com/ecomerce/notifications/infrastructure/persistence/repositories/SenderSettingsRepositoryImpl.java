package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import com.ecomerce.notifications.domain.aggregate.SenderSettings;
import com.ecomerce.notifications.domain.repository.SenderSettingsRepository;
import com.ecomerce.notifications.infrastructure.persistence.entities.SenderSettingsJpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto SenderSettingsRepository.
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
public class SenderSettingsRepositoryImpl implements SenderSettingsRepository {

    private final SenderSettingsJpaRepository senderSettingsJpaRepository;

    public SenderSettingsRepositoryImpl(SenderSettingsJpaRepository senderSettingsJpaRepository) {
        this.senderSettingsJpaRepository = senderSettingsJpaRepository;
    }

    @Override
    public Optional<SenderSettings> findById(UUID id) {
        return senderSettingsJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SenderSettings> findByKey(SenderSettingsKey key) {
        return senderSettingsJpaRepository.findByKey(key).map(this::toDomain);
    }

    @Override
    @Transactional
    public SenderSettings save(SenderSettings entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        SenderSettingsJpa jpa = entity.getId() != null
                ? senderSettingsJpaRepository.findById(entity.getId()).orElseGet(SenderSettingsJpa::new)
                : new SenderSettingsJpa();
        applyToJpa(entity, jpa);
        return toDomain(senderSettingsJpaRepository.save(jpa));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        senderSettingsJpaRepository.deleteById(id);
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private SenderSettings toDomain(SenderSettingsJpa jpa) {
        return new SenderSettings(
                jpa.getId(),
                jpa.getKey(),
                jpa.getSenderAddress(),
                jpa.getSenderName(),
                jpa.getReplyToAddress(),
                jpa.getLockVersion());
    }

    private void applyToJpa(SenderSettings domain, SenderSettingsJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setKey(domain.getKey());
        jpa.setSenderAddress(domain.getSenderAddress());
        jpa.setSenderName(domain.getSenderName());
        jpa.setReplyToAddress(domain.getReplyToAddress());
        jpa.setLockVersion(domain.getLockVersion());
    }
}
