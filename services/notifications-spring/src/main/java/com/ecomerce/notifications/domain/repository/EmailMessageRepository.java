package com.ecomerce.notifications.domain.repository;

import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de persistencia del agregado EmailMessage; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface EmailMessageRepository {

    Optional<EmailMessage> findById(UUID id);

    Optional<EmailMessage> findByRequestedByAndIdempotencyKey(String requestedBy, String idempotencyKey);

    Page<EmailMessage> list(Pageable pageable);

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
    List<EmailMessage> claimForStalledDispatchQueuedMessages();

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
    Optional<EmailMessage> claimForSendQueuedMessage(UUID id);

    /** Los filtros de listMessages (un filtro a null no restringe); recipient casa ignorando mayúsculas. */
    record ListMessagesFilter(String requestedBy, com.ecomerce.notifications.domain.enums.MessageStatus status, String templateCode, String recipient, Instant requestedFrom, Instant requestedTo) {
    }

    Page<EmailMessage> search(ListMessagesFilter filter, Pageable pageable);

    /** Ids de los mensajes queued más antiguos (requestedAt e id ascendentes), hasta {@code limit}. */
    List<UUID> findQueuedIdsForDispatch(int limit);

    /**
     * Mensajes sent o failed con requestedAt anterior a {@code cutoff} y sin purgar, hasta {@code limit}, por
     * requestedAt e id ascendentes. Toma sus filas con bloqueo que SALTA las ya tomadas por otra ejecución, así
     * que dos purgas solapadas no se pisan: cada una se lleva las suyas. Solo vale dentro de una transacción.
     */
    List<EmailMessage> lockPurgeCandidates(Instant cutoff, int limit);

    EmailMessage save(EmailMessage entity);

    void deleteById(UUID id);
}
