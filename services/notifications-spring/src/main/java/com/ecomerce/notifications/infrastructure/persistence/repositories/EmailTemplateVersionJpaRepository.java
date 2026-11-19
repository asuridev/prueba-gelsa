package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.infrastructure.persistence.entities.EmailTemplateVersionJpa;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lectura paginada del historial de versiones de una plantilla; la escritura va siempre por el agregado. */
public interface EmailTemplateVersionJpaRepository extends JpaRepository<EmailTemplateVersionJpa, UUID> {

    Page<EmailTemplateVersionJpa> findByEmailTemplateId(UUID emailTemplateId, Pageable pageable);
}
