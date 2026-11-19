package com.ecomerce.notifications.domain.repository;

import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import com.ecomerce.notifications.domain.aggregate.SenderSettings;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del agregado SenderSettings; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface SenderSettingsRepository {

    Optional<SenderSettings> findById(UUID id);

    Optional<SenderSettings> findByKey(SenderSettingsKey key);

    SenderSettings save(SenderSettings entity);

    void deleteById(UUID id);
}
