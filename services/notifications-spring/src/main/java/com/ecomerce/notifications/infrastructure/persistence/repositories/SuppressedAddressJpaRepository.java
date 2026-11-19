package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.infrastructure.persistence.entities.SuppressedAddressJpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuppressedAddressJpaRepository extends JpaRepository<SuppressedAddressJpa, UUID> {

    Optional<SuppressedAddressJpa> findByAddressNormalized(String addressNormalized);

    java.util.List<SuppressedAddressJpa> findByAddressNormalizedInAndStatus(java.util.Collection<String> addressesNormalized, com.ecomerce.notifications.domain.enums.SuppressionStatus status);

    org.springframework.data.domain.Page<SuppressedAddressJpa> findByStatus(com.ecomerce.notifications.domain.enums.SuppressionStatus status, org.springframework.data.domain.Pageable pageable);
}
