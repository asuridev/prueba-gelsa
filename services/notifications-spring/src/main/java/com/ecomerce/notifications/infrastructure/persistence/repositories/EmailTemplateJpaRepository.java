package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.infrastructure.persistence.entities.EmailTemplateJpa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmailTemplateJpaRepository extends JpaRepository<EmailTemplateJpa, UUID>, JpaSpecificationExecutor<EmailTemplateJpa> {

    @EntityGraph(attributePaths = { "versions" })
    Optional<EmailTemplateJpa> findByCode(String code);

    @EntityGraph(attributePaths = { "versions" })
    @Override
    Optional<EmailTemplateJpa> findById(UUID id);
}
