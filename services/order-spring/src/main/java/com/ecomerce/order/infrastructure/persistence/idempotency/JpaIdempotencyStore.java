package com.ecomerce.order.infrastructure.persistence.idempotency;

import com.ecomerce.order.domain.idempotency.IdempotencyConflictException;
import com.ecomerce.order.domain.idempotency.IdempotencyStore;
import com.ecomerce.order.infrastructure.persistence.purge.BatchedPurge;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JPA del registro de idempotencia.
 *
 * <p><b>Transaccionalidad</b>: {@code save} usa la propagación por defecto
 * (REQUIRED), es decir, se une a la transacción del caso de uso — al revés que
 * IdempotencyGuard, que registra en REQUIRES_NEW. La diferencia es deliberada:
 * allí el registro debe sobrevivir al fallo del handler (el mensaje ya se
 * consumió); aquí el registro y el recurso creado tienen que commitear juntos,
 * porque una clave marcada sin recurso detrás haría que el reintento de una
 * operación fallida devolviese una respuesta que nunca existió.
 *
 * <p><b>Carreras</b>: dos peticiones simultáneas con la misma clave insertan la
 * misma PK y la BD arbitra; la que pierde revierte entera, así que de las dos
 * peticiones idénticas se ejecutó exactamente una. Con varias réplicas esto deja
 * de ser un caso raro: las dos peticiones ni siquiera están en el mismo proceso, y
 * nada salvo la clave primaria las coordina.
 *
 * <p>El desenlace se traduce a {@link IdempotencyConflictException} en vez de
 * dejarlo subir crudo: sin code propio, el cliente recibiría un 409 indistinguible
 * de un conflicto de negocio de la misma operación.
 *
 * <p>La cadencia de la purga sale de parameters/, nunca del código.
 */
@Component
public class JpaIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(JpaIdempotencyStore.class);

    private final IdempotencyRecordJpaRepository repository;

    @Value("${idempotency-record.purge.batch-size:1000}")
    private int purgeBatchSize;

    @Value("${idempotency-record.purge.max-batches:500}")
    private int purgeMaxBatches;

    public JpaIdempotencyStore(IdempotencyRecordJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredRequest> find(String scope, String idempotencyKey) {
        Instant now = Instant.now();
        return repository
                .findById(new IdempotencyRecordJpa.IdempotencyRecordId(scope, idempotencyKey))
                // Una fila caducada es como si no estuviera: la ventana de
                // deduplicación la fija el diseño, no la purga (que va por lotes).
                .filter(stored -> stored.getExpiresAt().isAfter(now))
                .map(stored -> new StoredRequest(stored.getSignature(), stored.getResourceId()));
    }

    @Override
    @Transactional
    public void save(String scope, String idempotencyKey, String signature, String resourceId, long ttlSeconds) {
        Instant now = Instant.now();
        IdempotencyRecordJpa.IdempotencyRecordId key =
                new IdempotencyRecordJpa.IdempotencyRecordId(scope, idempotencyKey);
        // Una fila CADUCADA es como si no estuviera —es lo que ya asume find(...)—, y
        // tiene que serlo también aquí. Si no, la clave queda inutilizable entre su
        // caducidad y la purga: find la ignora, el handler ejecuta, y la inserción
        // choca con una fila que ya no protege nada. El cliente recibiría un 409
        // IDEMPOTENCY_KEY_IN_PROGRESS durante casi un día, y la ventana real de
        // deduplicación la fijaría la cadencia de la purga en vez del ttlSeconds del
        // diseño, que es justo lo que ese campo compra.
        //
        // Se retira por el repositorio y no con un borrado masivo a propósito:
        // find(...) acaba de cargar esa fila en ESTE contexto de persistencia, y un
        // delete masivo no la desasocia — la inserción siguiente chocaría contra la
        // copia gestionada en memoria en vez de contra la base.
        repository.findById(key)
                .filter(stored -> !stored.getExpiresAt().isAfter(now))
                .ifPresent(expired -> {
                    repository.delete(expired);
                    // Antes del INSERT, y en esta transacción: si no, JPA reordena y la
                    // inserción sale primero, contra la fila que aún está.
                    repository.flush();
                });
        try {
            // saveAndFlush del REPOSITORIO, no entityManager.persist a mano: la
            // traduccion de excepciones de Spring ocurre al salir de un metodo
            // proxeado, y el proxy de Spring Data convierte la violacion de Hibernate
            // en DataIntegrityViolationException — que es lo que captura el catch de
            // abajo. Con el EntityManager compartido dentro de un @Component no hay
            // proxy que traduzca, sale un ConstraintViolationException crudo que
            // ningun catch reconoce, y la carrera acaba en 500 en vez de en su code.
            // El INSERT lo fuerza Persistable.isNew() en la entidad, no este metodo.
            // Y saveAndFlush y no save porque JPA difiere el INSERT hasta el commit:
            // sin el flush la violacion saldria por el commit del UseCaseMediator,
            // donde ya nadie puede distinguirla ni traducirla.
            repository.saveAndFlush(new IdempotencyRecordJpa(
                    key,
                    signature,
                    resourceId,
                    now,
                    now.plusSeconds(ttlSeconds)));
        } catch (DataIntegrityViolationException
                | PessimisticLockingFailureException
                | TransactionSystemException concurrent) {
            // Las DOS familias. Dos peticiones con la misma clave a la vez no siempre chocan
            // por violacion de restriccion: InnoDB hace esperar al segundo INSERT sobre el
            // lock del primero, y si el desenlace tarda sale por lock-wait timeout o
            // deadlock, que Spring traduce a PessimisticLockingFailure. Capturando solo la
            // primera, la carrera acaba en 500 en vez de en el code que el diseno declara —
            // y solo en el caso concurrente, que es el unico que este registro existe para
            // arbitrar y el que menos se reproduce a mano.
            //
            // Y la tercera llega por otro camino: un fallo al CONFIRMAR la transaccion no
            // viaja como excepcion de acceso a datos sino como TransactionSystemException,
            // que no es DataAccessException y se escaparia de las dos anteriores. Significa
            // lo mismo: el registro no quedo escrito, luego esta peticion perdio la carrera.
            throw new IdempotencyConflictException(scope, idempotencyKey, concurrent);
        }
    }

    /**
     * Por lotes y SIN transacción propia: cada lote confirma en la suya (ver BatchedPurge).
     */
    public void purge() {
        Instant cutoff = Instant.now();
        long deleted = BatchedPurge.run("idempotency_record", purgeBatchSize, purgeMaxBatches,
                position -> repository.findPurgeBoundary(cutoff, PageRequest.of(position, 1)),
                upTo -> repository.deleteExpiredBefore(cutoff, upTo), cutoff);
        if (deleted > 0) {
            log.info("Idempotencia HTTP: purgadas {} claves caducadas", deleted);
        }
    }
}
