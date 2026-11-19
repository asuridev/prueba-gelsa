package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.application.support.SweepPass;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.domain.enums.MessageStatus;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;
import com.ecomerce.notifications.domain.valueobject.SenderSnapshot;
import com.ecomerce.notifications.domain.valueobject.VariableValue;
import com.ecomerce.notifications.infrastructure.persistence.entities.EmailMessageJpa;
import com.ecomerce.notifications.infrastructure.persistence.entities.VariableValueJpa;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.ecomerce.notifications.infrastructure.persistence.support.TextFold;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto EmailMessageRepository.
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
public class EmailMessageRepositoryImpl implements EmailMessageRepository {

    private static final Sort TIE_BREAKER = Sort.by(Sort.Order.asc("id"));

    /**
     * Cuánto trabajo cabe en UNA pasada del barrido dispatchQueuedMessages.
     *
     * NO lo declara el diseño: es capacidad, la familia de los backoffs, y se ajusta con datos
     * de producción delante. Sin cota, una tanda con 50.000 filas atrasadas son 50.000 en una
     * sola pasada. Misma decisión —y mismo sitio— que outbox.relay.batch-size.
     */
    @Value("${sweep.dispatch-queued-messages.batch-size:100}")
    private int dispatchQueuedMessagesBatchSize;

    /**
     * Cuánto puede llevar un EmailMessage en sending antes de darlo por abandonado, en
     * segundos. Lo DECLARA el diseño: es el parámetro {@code sendingTimeoutMinutes}
     * (en minutes), porque expirar aquí tiene consecuencia de negocio. Se lee de la
     * MISMA propiedad que puebla el record de parámetros: no hay un segundo plazo que ajustar.
     */
    @Value("#{${notifications.sending-timeout-minutes} * 60}")
    private long stalledDispatchQueuedMessagesAfterSeconds;

    private final EmailMessageJpaRepository emailMessageJpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EmailMessageRepositoryImpl(EmailMessageJpaRepository emailMessageJpaRepository, ApplicationEventPublisher eventPublisher) {
        this.emailMessageJpaRepository = emailMessageJpaRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<EmailMessage> findById(UUID id) {
        return emailMessageJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<EmailMessage> findByRequestedByAndIdempotencyKey(String requestedBy, String idempotencyKey) {
        return emailMessageJpaRepository.findByRequestedByAndIdempotencyKey(requestedBy, idempotencyKey).map(this::toDomain);
    }

    @Override
    public Page<EmailMessage> list(Pageable pageable) {
        return emailMessageJpaRepository.findAll(withStableOrder(pageable)).map(this::toDomain);
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

    /**
     * Rescata hasta {@code batchSize} EmailMessage ATASCADOS en sending: los devuelve SIGUIENDO en sending, con {@code sendingSince} renovado, para que el handler los pase a failed.
     *
     * <p><b>Reclama, no lee.</b> Puede correr a la vez en varias réplicas: con
     * {@code @Scheduled}, en todas; con el cronómetro, cuando un tick se repite al vencer su arriendo. El reclamo es
     * un UPDATE condicional que solo casa si la fila sigue atascada, y renueva el reloj en
     * la misma escritura: la fila que otra réplica rescató antes ya no parece atascada y no
     * aparece aquí. Se COMMITEA antes de volver (transacción propia).
     *
     * <p><b>No cambia el estado: lo arrienda.</b> La transición a failed es del DOMINIO —es la
     * que fija los campos que ese estado exige (un motivo, un instante)— y la hace el handler
     * con el agregado que esto devuelve. Moverla aquí dejaba la fila en failed sin esos
     * campos, violando el invariante en el estado que la base ya expone (corrida notifications,
     * 2026-09-30). Y si el ciclo muere entre el reclamo y la transición, la fila sigue en
     * sending con el reloj renovado: otro rescate la recoge pasado el plazo.
     *
     * <p><b>Y solo se lleva lo ABANDONADO.</b> sending es un estado EN VUELO: hay
     * una instancia trabajando en esas filas ahora mismo. Entran solo las filas cuyo
     * {@code sendingSince} es más viejo que el parámetro del diseño {@code sendingTimeoutMinutes} (en minutes).
     * Ese plazo tiene que quedar por encima de lo que tarda un ciclo completo.
     *
     * <p>Lo que devuelve es trabajo que alguien dejó a medias, no trabajo nuevo: si el ciclo que
     * murió ya produjo un efecto externo irreversible, repetirlo lo duplica. Actúa en consecuencia.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> claimForStalledDispatchQueuedMessages() {
        // La cota del rescate: solo lo que lleva atascado más que el plazo configurado.
        Instant staleBefore = Instant.now().minusSeconds(stalledDispatchQueuedMessagesAfterSeconds);
        // El reloj renovado: el rescate ARRIENDA la fila, no la mueve de estado.
        Instant leasedAt = Instant.now();
        List<MessageStatus> states = List.of(MessageStatus.SENDING);
        List<UUID> candidates = emailMessageJpaRepository.candidatesForStalledDispatchQueuedMessages(states, staleBefore, PageRequest.of(0, dispatchQueuedMessagesBatchSize));
        List<EmailMessage> claimed = new ArrayList<>();
        for (UUID id : candidates) {
            // 1 = la fila era mía; 0 = otra réplica la reclamó entre el select y el update.
            if (emailMessageJpaRepository.claimForStalledDispatchQueuedMessages(id, states, staleBefore, leasedAt) == 1) {
                emailMessageJpaRepository.findById(id).map(this::toDomain).ifPresent(claimed::add);
            }
        }
        // Lote lleno: puede que queden candidatos, y el tick del cronómetro repite la pasada (drenaje).
        SweepPass.claimed(candidates.size(), dispatchQueuedMessagesBatchSize);
        return claimed;
    }

    /**
     * Reclama ESTE EmailMessage para sendQueuedMessage: lo pasa a sending solo si sigue
     * en queued, y estampa sendingSince. Devuelve
     * el agregado ya reclamado, o vacío si otra ejecución llegó antes.
     *
     * <p><b>Y confirma antes de volver</b> (transacción propia). Eso es lo que lo hace una
     * guarda y no una anotación en memoria: sendQueuedMessage produce un efecto externo que
     * NO se deshace, así que la marca tiene que existir para todo el mundo ANTES de
     * producirlo. Si el proceso cae después del efecto y antes del commit final, la fila se
     * queda en sending —que es justo lo que busca el rescate del barrido— en vez de volver
     * a queued y repetirse.
     *
     * <p>El vacío es la carrera perdida, y se traduce al error que el diseño declare para
     * «ya no está disponible». No es un caso excepcional: es el caso normal cuando dos
     * ejecuciones coinciden.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<EmailMessage> claimForSendQueuedMessage(UUID id) {
        // REQUIRES_NEW, y no la transacción del llamante: si la marca esperase al commit de
        // aquella, no existiría para nadie durante el efecto externo, que es cuando hace falta.
        int claimed = emailMessageJpaRepository.claimForSendQueuedMessage(
                id, List.of(MessageStatus.QUEUED), MessageStatus.SENDING, Instant.now());
        if (claimed == 0) {
            return Optional.empty();
        }
        return emailMessageJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<EmailMessage> search(ListMessagesFilter filter, Pageable pageable) {
        Specification<EmailMessageJpa> specification = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.requestedBy() != null) {
                where.add(cb.equal(root.get("requestedBy"), filter.requestedBy()));
            }
            if (filter.status() != null) {
                where.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.templateCode() != null) {
                where.add(cb.equal(root.get("templateCode"), filter.templateCode()));
            }
            if (filter.recipient() != null) {
                // Los destinatarios se guardan en minúsculas: se pliega el filtro igual (compare: ignore-case).
                where.add(cb.isMember(TextFold.foldCase(filter.recipient()), root.<List<String>>get("recipients")));
            }
            if (filter.requestedFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Instant>get("requestedAt"), filter.requestedFrom()));
            }
            if (filter.requestedTo() != null) {
                where.add(cb.lessThan(root.<Instant>get("requestedAt"), filter.requestedTo()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return emailMessageJpaRepository.findAll(specification, withStableOrder(pageable)).map(this::toDomain);
    }

    @Override
    public List<UUID> findQueuedIdsForDispatch(int limit) {
        return emailMessageJpaRepository.findIdsByStatusOldestFirst(MessageStatus.QUEUED, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public List<EmailMessage> lockPurgeCandidates(Instant cutoff, int limit) {
        List<UUID> ids = emailMessageJpaRepository.lockPurgeCandidateIds(
                List.of(MessageStatus.SENT, MessageStatus.FAILED), cutoff, PageRequest.of(0, limit));
        List<EmailMessage> result = new ArrayList<>();
        for (UUID id : ids) {
            emailMessageJpaRepository.findById(id).map(this::toDomain).ifPresent(result::add);
        }
        return result;
    }

    @Override
    @Transactional
    public EmailMessage save(EmailMessage entity) {
        // La guarda del agregado (DSL 2.31): nada se escribe si una invariante no se cumple.
        entity.ensureInvariants();
        EmailMessageJpa jpa = entity.getId() != null
                ? emailMessageJpaRepository.findById(entity.getId()).orElseGet(EmailMessageJpa::new)
                : new EmailMessageJpa();
        applyToJpa(entity, jpa);
        EmailMessage saved = toDomain(emailMessageJpaRepository.save(jpa));
        entity.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        emailMessageJpaRepository.deleteById(id);
    }

    // ── Mapeo domain ↔ JPA ───────────────────────────────────────────────────

    private EmailMessage toDomain(EmailMessageJpa jpa) {
        return new EmailMessage(
                jpa.getId(),
                jpa.getRequestedBy(),
                jpa.getIdempotencyKey(),
                jpa.getRecipients(),
                jpa.getRenderedSubject(),
                jpa.getVariableValues().stream().map(e -> new VariableValue(e.getName(), e.getValue())).toList(),
                new SenderSnapshot(jpa.getSenderAddress(), jpa.getSenderName(), jpa.getSenderReplyToAddress()),
                jpa.getTemplateCode(),
                jpa.getTemplateVersionNumber(),
                jpa.getTemplateVersionId(),
                jpa.getStatus(),
                jpa.getRequestedAt(),
                jpa.getSendingSince(),
                jpa.getSentAt(),
                jpa.getFailedAt(),
                jpa.getFailureReason(),
                jpa.getPersonalDataPurgedAt(),
                jpa.getLockVersion());
    }

    private void applyToJpa(EmailMessage domain, EmailMessageJpa jpa) {
        jpa.setId(domain.getId());
        jpa.setRequestedBy(domain.getRequestedBy());
        jpa.setIdempotencyKey(domain.getIdempotencyKey());
        jpa.setRecipients(new ArrayList<>(domain.getRecipients()));
        jpa.setRenderedSubject(domain.getRenderedSubject());
        jpa.setVariableValues(new ArrayList<VariableValueJpa>(domain.getVariableValues().stream().map(v -> {
                    VariableValueJpa e = new VariableValueJpa();
                    e.setName(v.name());
                    e.setValue(v.value());
                    return e;
                }).toList()));
        jpa.setSenderAddress(domain.getSender() != null ? domain.getSender().address() : null);
        jpa.setSenderName(domain.getSender() != null ? domain.getSender().name() : null);
        jpa.setSenderReplyToAddress(domain.getSender() != null ? domain.getSender().replyToAddress() : null);
        jpa.setTemplateCode(domain.getTemplateCode());
        jpa.setTemplateVersionNumber(domain.getTemplateVersionNumber());
        jpa.setTemplateVersionId(domain.getTemplateVersionId());
        jpa.setStatus(domain.getStatus());
        jpa.setRequestedAt(domain.getRequestedAt());
        jpa.setSendingSince(domain.getSendingSince());
        jpa.setSentAt(domain.getSentAt());
        jpa.setFailedAt(domain.getFailedAt());
        jpa.setFailureReason(domain.getFailureReason());
        jpa.setPersonalDataPurgedAt(domain.getPersonalDataPurgedAt());
        jpa.setLockVersion(domain.getLockVersion());
    }
}
