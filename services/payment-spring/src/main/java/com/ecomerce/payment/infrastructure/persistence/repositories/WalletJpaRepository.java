package com.ecomerce.payment.infrastructure.persistence.repositories;

import com.ecomerce.payment.infrastructure.persistence.entities.WalletJpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletJpaRepository extends JpaRepository<WalletJpa, UUID> {

    @EntityGraph(attributePaths = { "paymentMethods" })
    Optional<WalletJpa> findByCustomerRef(String customerRef);

    @EntityGraph(attributePaths = { "paymentMethods" })
    @Override
    Optional<WalletJpa> findById(UUID id);
}
