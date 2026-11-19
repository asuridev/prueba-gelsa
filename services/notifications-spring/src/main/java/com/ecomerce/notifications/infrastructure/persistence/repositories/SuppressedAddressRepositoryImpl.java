package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.domain.aggregate.SuppressedAddress;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;
import com.ecomerce.notifications.infrastructure.persistence.entities.SuppressedAddressJpa;
import com.ecomerce.notifications.infrastructure.persistence.support.TextFold;
import com.ecomerce.notifications.domain.enums.SuppressionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto SuppressedAddressRepository.
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
public class SuppressedAddressRepositoryImpl implements SuppressedAddressRepository {

    private static final Sort TIE_BREAKER = Sort.by(Sort.Order.asc("id"));

    private final SuppressedAddressJpaRepository suppressedAddressJpaRepository;

    public SuppressedAddressRepositoryImpl(SuppressedAddressJpaRepository suppressedAddressJpaRepository) {
        this.suppressedAddressJpaRepository = suppressedAddressJpaRepository;
    }

    @Override
    public Optional<SuppressedAddress> findById(UUID id) {
        return suppressedAddressJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SuppressedAddress> findByAddress(String address) {
        return suppressedAddressJpaRepository.findByAddressNormalized(TextFold.foldCase(address)).map(this::toDomain);
    }

    @Override
    public Page<SuppressedAddress> list(Pageable pageable) {
        return suppressedAddressJpaRepository.findAll(withStableOrder(pageable)).map(this::toDomain);
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
    public Set<String> findActiveAddresses(Collection<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return Set.of();
        }
        List<String> folded = addresses.stream().map(TextFold::foldCase).distinct().toList();
        return suppressedAddressJpaRepository
                .findByAddressNormalizedInAndStatus(folded, SuppressionStatus.ACTIVE).stream()
                .map(SuppressedAddressJpa::getAddressNormalized)
                .collect(Collectors.toSet());
    }

    @Override
    public Page<SuppressedAddress> listActive(Pageable pageable) {
        return suppressedAddressJpaRepository.findByStatus(SuppressionStatus.ACTIVE, withStableOrder(pageable)).map(this::toDomain);
    }

    @Override
    @Transactional
    public SuppressedAddress save(SuppressedAddress entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        SuppressedAddressJpa jpa = entity.getId() != null
                ? suppressedAddressJpaRepository.findById(entity.getId()).orElseGet(SuppressedAddressJpa::new)
                : new SuppressedAddressJpa();
        applyToJpa(entity, jpa);
        return toDomain(suppressedAddressJpaRepository.save(jpa));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        suppressedAddressJpaRepository.deleteById(id);
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private SuppressedAddress toDomain(SuppressedAddressJpa jpa) {
        return new SuppressedAddress(
                jpa.getId(),
                jpa.getAddress(),
                jpa.getReason(),
                jpa.getNotes(),
                jpa.getStatus(),
                jpa.getSuppressedAt(),
                jpa.getReleasedAt(),
                jpa.getLockVersion());
    }

    private void applyToJpa(SuppressedAddress domain, SuppressedAddressJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setAddress(domain.getAddress());
        jpa.setAddressNormalized(TextFold.foldCase(domain.getAddress()));
        jpa.setReason(domain.getReason());
        jpa.setNotes(domain.getNotes());
        jpa.setStatus(domain.getStatus());
        jpa.setSuppressedAt(domain.getSuppressedAt());
        jpa.setReleasedAt(domain.getReleasedAt());
        jpa.setLockVersion(domain.getLockVersion());
    }
}
