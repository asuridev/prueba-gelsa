package com.ecomerce.order.infrastructure.persistence.repositories;

import com.ecomerce.order.infrastructure.persistence.entities.ProductJpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductJpa, UUID> {

    Optional<ProductJpa> findById(UUID id);
}
