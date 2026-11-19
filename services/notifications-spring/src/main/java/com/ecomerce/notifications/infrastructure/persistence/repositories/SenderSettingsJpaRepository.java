package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import com.ecomerce.notifications.infrastructure.persistence.entities.SenderSettingsJpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenderSettingsJpaRepository extends JpaRepository<SenderSettingsJpa, UUID> {

    Optional<SenderSettingsJpa> findByKey(SenderSettingsKey key);
}
