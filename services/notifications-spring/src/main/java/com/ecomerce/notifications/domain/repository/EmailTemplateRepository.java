package com.ecomerce.notifications.domain.repository;

import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de persistencia del agregado EmailTemplate; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface EmailTemplateRepository {

    Optional<EmailTemplate> findById(UUID id);

    Optional<EmailTemplate> findByCode(String code);

    Page<EmailTemplate> list(Pageable pageable);

    /**
     * Los filtros de listTemplates, como los declara el diseño (DSL 2.22). Un filtro a null no restringe.
     */
    record ListTemplatesFilter(Boolean hasActiveVersion) {
    }

    /** La búsqueda de listTemplates con los filtros del diseño y solo esos. */
    Page<EmailTemplate> listTemplates(ListTemplatesFilter filter, Pageable pageable);

    /** Historial de versiones de una plantilla, paginado y ordenado por quien llama. */
    Page<EmailTemplateVersion> listVersions(UUID templateId, Pageable pageable);

    /** Vuelca a la base las escrituras pendientes de la transacción (entre el retiro y el alta de un relevo). */
    void flushPendingWrites();

    EmailTemplate save(EmailTemplate entity);

    void deleteById(UUID id);
}
