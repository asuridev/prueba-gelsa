package com.ecomerce.notifications.domain.repository;

import com.ecomerce.notifications.domain.aggregate.SuppressedAddress;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de persistencia del agregado SuppressedAddress; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface SuppressedAddressRepository {

    Optional<SuppressedAddress> findById(UUID id);

    Optional<SuppressedAddress> findByAddress(String address);

    Page<SuppressedAddress> list(Pageable pageable);

    /** Las direcciones de {@code addresses} que tienen una supresión active, en minúsculas (una sola consulta). */
    Set<String> findActiveAddresses(Collection<String> addresses);

    /** Solo las supresiones active. */
    Page<SuppressedAddress> listActive(Pageable pageable);

    SuppressedAddress save(SuppressedAddress entity);

    void deleteById(UUID id);
}
