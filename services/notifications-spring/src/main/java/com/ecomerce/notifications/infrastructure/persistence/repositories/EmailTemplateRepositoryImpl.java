package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;
import com.ecomerce.notifications.domain.valueobject.TemplateVariable;
import com.ecomerce.notifications.infrastructure.persistence.entities.EmailTemplateJpa;
import com.ecomerce.notifications.infrastructure.persistence.entities.EmailTemplateVersionJpa;
import com.ecomerce.notifications.infrastructure.persistence.entities.TemplateVariableJpa;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto EmailTemplateRepository.
 *
 * <p>El {@code @Transactional(readOnly = true)} de clase hace que este adaptador se baste solo.
 * Casi siempre es un no-op: los caminos normales (REST, listeners) llegan aquí dentro de la
 * transacción que abrió el {@code UseCaseMediator}, y la propagación por defecto se une a ella
 * ignorando el flag. Importa en el único camino que NO trae transacción: el barrido de
 * reconciliación, que se despacha con {@code dispatchWithoutTransaction} justo para poder colocar
 * sus commits. Sin esto, una consulta que devuelva un agregado con colecciones LAZY reventaría
 * ahí con {@code LazyInitializationException}.
 *
 * <p>Por eso mismo, <b>toda escritura lleva su {@code @Transactional} de método</b>: sin él
 * heredaría el {@code readOnly} de la clase. Si añades una consulta de escritura a este
 * adaptador, anótala — el fallo es en caliente y no silencioso, que es lo que se busca.
 */
@Component
@Transactional(readOnly = true)
public class EmailTemplateRepositoryImpl implements EmailTemplateRepository {

    private static final Sort TIE_BREAKER = Sort.by(Sort.Order.asc("id"));

    private final EmailTemplateJpaRepository emailTemplateJpaRepository;
    private final EmailTemplateVersionJpaRepository emailTemplateVersionJpaRepository;

    public EmailTemplateRepositoryImpl(EmailTemplateJpaRepository emailTemplateJpaRepository, EmailTemplateVersionJpaRepository emailTemplateVersionJpaRepository) {
        this.emailTemplateVersionJpaRepository = emailTemplateVersionJpaRepository;
        this.emailTemplateJpaRepository = emailTemplateJpaRepository;
    }

    @Override
    public Optional<EmailTemplate> findById(UUID id) {
        return emailTemplateJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<EmailTemplate> findByCode(String code) {
        return emailTemplateJpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    public Page<EmailTemplate> list(Pageable pageable) {
        return emailTemplateJpaRepository.findAll(withStableOrder(pageable)).map(this::toDomain);
    }

    /**
     * Añade el id como último criterio de orden si no está ya. Sin desempate, dos
     * páginas consecutivas de la misma consulta pueden repetir una fila y omitir
     * otra: cuando el ORDER BY empata, la base de datos no garantiza un orden
     * estable entre consultas. Se aplica también al orden que pida el cliente,
     * que es justo el caso que un @PageableDefault no cubre.
     */
    private static Pageable withStableOrder(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(TIE_BREAKER));
    }

    @Override
    public Page<EmailTemplate> listTemplates(EmailTemplateRepository.ListTemplatesFilter filter, Pageable pageable) {
        Specification<EmailTemplateJpa> specification = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.hasActiveVersion() != null) {
                where.add(cb.equal(root.get("hasActiveVersion"), filter.hasActiveVersion()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return emailTemplateJpaRepository.findAll(specification, withStableOrder(pageable)).map(this::toDomain);
    }

    @Override
    public Page<EmailTemplateVersion> listVersions(UUID templateId, Pageable pageable) {
        return emailTemplateVersionJpaRepository.findByEmailTemplateId(templateId, withStableOrder(pageable)).map(this::toDomain);
    }

    @Override
    @Transactional
    public void flushPendingWrites() {
        emailTemplateJpaRepository.flush();
    }

    @Override
    @Transactional
    public EmailTemplate save(EmailTemplate entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        EmailTemplateJpa jpa = entity.getId() != null
                ? emailTemplateJpaRepository.findById(entity.getId()).orElseGet(EmailTemplateJpa::new)
                : new EmailTemplateJpa();
        applyToJpa(entity, jpa);
        // saveAndFlush: el índice único de la versión active se comprueba por fila, así que el handler que releva
        // (archiva una y activa otra) guarda en dos pasos y cada uno tiene que llegar a la base antes del siguiente.
        return toDomain(emailTemplateJpaRepository.saveAndFlush(jpa));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        emailTemplateJpaRepository.deleteById(id);
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private EmailTemplate toDomain(EmailTemplateJpa jpa) {
        return new EmailTemplate(
                jpa.getId(),
                jpa.getCode(),
                jpa.getName(),
                jpa.getDescription(),
                jpa.getHasActiveVersion(),
                jpa.getVersions().stream().map(this::toDomain).toList(),
                jpa.getLockVersion());
    }

    private void applyToJpa(EmailTemplate domain, EmailTemplateJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setCode(domain.getCode());
        jpa.setName(domain.getName());
        jpa.setDescription(domain.getDescription());
        jpa.setHasActiveVersion(domain.getHasActiveVersion());
        Map<UUID, EmailTemplateVersionJpa> versionsManaged = new HashMap<>();
        for (EmailTemplateVersionJpa child : jpa.getVersions()) versionsManaged.put(child.getId(), child);
        List<EmailTemplateVersionJpa> versionsReconciled = new ArrayList<>();
        for (EmailTemplateVersion child : domain.getVersions()) {
            EmailTemplateVersionJpa childJpa = child.getId() != null ? versionsManaged.get(child.getId()) : null;
            if (childJpa == null) childJpa = new EmailTemplateVersionJpa();
            applyToJpa(child, childJpa);
            childJpa.setEmailTemplate(jpa);
            versionsReconciled.add(childJpa);
        }
        jpa.getVersions().clear();
        jpa.getVersions().addAll(versionsReconciled);
        jpa.setLockVersion(domain.getLockVersion());
    }

    private EmailTemplateVersion toDomain(EmailTemplateVersionJpa jpa) {
        return new EmailTemplateVersion(
                jpa.getId(),
                jpa.getVersionNumber(),
                jpa.getSubject(),
                jpa.getHtmlBody(),
                jpa.getTextBody(),
                jpa.getVariables().stream().map(e -> new TemplateVariable(e.getName(), e.getDescription(), e.getRequired())).toList(),
                jpa.getStatus(),
                jpa.getPublishedAt());
    }

    private void applyToJpa(EmailTemplateVersion domain, EmailTemplateVersionJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setVersionNumber(domain.getVersionNumber());
        jpa.setSubject(domain.getSubject());
        jpa.setHtmlBody(domain.getHtmlBody());
        jpa.setTextBody(domain.getTextBody());
        jpa.setVariables(new ArrayList<TemplateVariableJpa>(domain.getVariables().stream().map(v -> {
                    TemplateVariableJpa e = new TemplateVariableJpa();
                    e.setName(v.name());
                    e.setDescription(v.description());
                    e.setRequired(v.required());
                    return e;
                }).toList()));
        jpa.setStatus(domain.getStatus());
        jpa.setPublishedAt(domain.getPublishedAt());
        // emailTemplate: lo estampa EmailTemplateRepositoryImpl al mapear su colección.
    }
}
