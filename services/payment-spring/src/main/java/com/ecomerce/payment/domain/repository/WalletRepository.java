package com.ecomerce.payment.domain.repository;

import com.ecomerce.payment.domain.aggregate.Wallet;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del agregado Wallet; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface WalletRepository {

    Optional<Wallet> findById(UUID id);

    Optional<Wallet> findByCustomerRef(String customerRef);

    Wallet save(Wallet entity);

    void deleteById(UUID id);
}
