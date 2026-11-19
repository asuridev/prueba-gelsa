package com.ecomerce.order.domain.repository;

import com.ecomerce.order.domain.aggregate.Product;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del agregado Product; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface ProductRepository {

    Optional<Product> findById(UUID id);

    Product save(Product entity);

    void deleteById(UUID id);
}
