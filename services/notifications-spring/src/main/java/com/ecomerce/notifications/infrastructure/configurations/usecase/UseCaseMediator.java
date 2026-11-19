package com.ecomerce.notifications.infrastructure.configurations.usecase;

import com.ecomerce.notifications.application.interfaces.Command;
import com.ecomerce.notifications.application.interfaces.CommandHandler;
import com.ecomerce.notifications.application.interfaces.Query;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.domain.errors.DomainException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fachada única de despacho de casos de uso: resuelve el handler registrado
 * para la clase del mensaje y lo invoca. Los controllers dependen solo de
 * este componente, no de los handlers concretos.
 *
 * La frontera transaccional del diseño vive aquí: las Query corren en
 * transacción readOnly y los Command en transacción de escritura, así los
 * handlers no dependen de Spring.
 *
 * <p>Con una excepción, {@link #dispatchWithoutTransaction}: los barridos que
 * llaman a un proveedor EN MEDIO de su trabajo no pueden correr bajo una
 * transacción abarcadora. Su javadoc explica por qué.
 *
 * <p>Si una operación necesita semántica transaccional especial, se resuelve en
 * el ADAPTADOR de repositorio, que sí vive en infraestructura y puede hablar con
 * Spring. El handler no: no importa Spring (constitution.md).
 */
@Component
public class UseCaseMediator {

    private static final Logger log = LoggerFactory.getLogger(UseCaseMediator.class);

    private final UseCaseContainer useCaseContainer;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;
    private final ObservationRegistry observationRegistry;

    public UseCaseMediator(UseCaseContainer useCaseContainer, PlatformTransactionManager transactionManager, ObservationRegistry observationRegistry) {
        this.useCaseContainer = useCaseContainer;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.observationRegistry = observationRegistry;
    }

    @SuppressWarnings("unchecked")
    public <R, Q extends Query<R>> R dispatch(Q query) {
        QueryHandler<Q, R> instance = (QueryHandler<Q, R>) useCaseContainer.resolve(query.getClass());
        return callUseCase(query, () -> readTransaction.execute(status -> instance.handle(query)));
    }

    @SuppressWarnings("unchecked")
    public <C extends Command> void dispatch(C command) {
        CommandHandler<C> instance = (CommandHandler<C>) useCaseContainer.resolve(command.getClass());
        runUseCase(command, () -> retryingWriteConflicts(() -> { writeTransaction.executeWithoutResult(status -> instance.handle(command)); return null; }));
    }

    @SuppressWarnings("unchecked")
    public <R, C extends ReturningCommand<R>> R dispatch(C command) {
        ReturningCommandHandler<C, R> instance = (ReturningCommandHandler<C, R>) useCaseContainer.resolve(command.getClass());
        return callUseCase(command, () -> retryingWriteConflicts(() -> writeTransaction.execute(status -> instance.handle(command))));
    }

    /**
     * Despacha un Command <b>sin abrir transacción</b>: las abre el adaptador de repositorio
     * en cada llamada, así que el barrido controla dónde cae cada commit.
     *
     * <p>Es para las operaciones con {@code schedule} que ACTÚAN sobre lo que encuentran —un
     * barrido de reconciliación—, donde la llamada al proveedor va EN MEDIO del trabajo. Su
     * garantía es un orden de commits: (1) reclamar los candidatos y confirmar, que es lo que
     * hace el reclamo visible a las demás réplicas; (2) llamar al proveedor, fuera de toda
     * transacción; (3) confirmar el desenlace. Una transacción abarcadora rompe (1) —el
     * reclamo no confirmaría hasta el final del lote— y rompe (2) —una conexión del pool
     * retenida por la latencia de un tercero, multiplicada por el tamaño del lote—.
     *
     * <p>Las demás operaciones con {@code schedule} (una purga, un cierre diario) NO usan este
     * camino: no llaman a nadie en medio y su transacción única es lo correcto.
     *
     * <p>Mismo razonamiento que el {@code OutboxRelay} de la rama documental, que tampoco es
     * {@code @Transactional}: abrir una transacción solo serviría para mantenerla abierta
     * durante I/O externo.
     *
     * <p>Ver docs/keel/conventions/dependencies.md § El orden dentro del barrido.
     */
    @SuppressWarnings("unchecked")
    public <C extends Command> void dispatchWithoutTransaction(C command) {
        CommandHandler<C> instance = (CommandHandler<C>) useCaseContainer.resolve(command.getClass());
        runUseCase(command, () -> instance.handle(command));
    }

    /** Igual que el anterior, para un barrido cuya operación declara `output`. */
    @SuppressWarnings("unchecked")
    public <R, C extends ReturningCommand<R>> R dispatchWithoutTransaction(C command) {
        ReturningCommandHandler<C, R> instance = (ReturningCommandHandler<C, R>) useCaseContainer.resolve(command.getClass());
        return callUseCase(command, () -> instance.handle(command));
    }

    private <T> T callUseCase(Object message, Supplier<T> action) {
        String operation = message.getClass().getSimpleName();
        Observation observation = observationOf(operation).start();
        try (Observation.Scope scope = observation.openScope()) {
            T result = logged(operation, action);
            observation.lowCardinalityKeyValue("keel.outcome", "ok");
            return result;
        } catch (DomainException ex) {
            // Un rechazo del dominio NO es un error del span: es un desenlace esperado (un 4xx).
            // Marcarlo como error llenaría de rojo el muestreo por cola justo con lo que funciona.
            observation.lowCardinalityKeyValue("keel.outcome", "rejected");
            throw ex;
        } catch (RuntimeException | Error ex) {
            observation.lowCardinalityKeyValue("keel.outcome", "error");
            // Con datos personales (DSL 2.28, PD-3) el span lleva solo la CLASE de una excepción de acceso a datos: su
            // mensaje es el del motor, y al violar una unicidad lleva el valor que chocó («Duplicate entry 'ana@…'»).
            // La traduce el @ControllerAdvice, fuera de esta observación.
            observation.error(ex instanceof DataAccessException ? new IllegalStateException(ex.getClass().getName()) : ex);
            throw ex;
        } finally {
            observation.stop();
        }
    }

    private void runUseCase(Object message, Runnable action) {
        callUseCase(message, () -> {
            action.run();
            return null;
        });
    }

    /**
     * El log de frontera del caso de uso. Solo nombres y códigos, nunca los valores del mensaje:
     * un Command lleva datos de quien llama, y lo que se loguea se queda en el backend de logs.
     *
     * <p>Niveles fijos: DEBUG si salió bien (en producción no se imprime), INFO si el dominio lo
     * rechazó —un 4xx es un resultado esperado, no un fallo del servicio— y ERROR si falló. La pila
     * de un fallo NO va aquí: la imprime el adaptador por el que entró (ApiExceptionHandler, el
     * contenedor del listener o el scheduler), y con las dos cada fallo saldría duplicado.
     */
    private static <T> T logged(String operation, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            log.atDebug()
                    .addKeyValue("keel.operation", operation)
                    .addKeyValue("keel.outcome", "ok")
                    .addKeyValue("keel.duration_ms", elapsedMillis(start))
                    .log("Caso de uso {}: ok", operation);
            return result;
        } catch (DomainException ex) {
            log.atInfo()
                    .addKeyValue("keel.operation", operation)
                    .addKeyValue("keel.outcome", "rejected")
                    .addKeyValue("keel.error_code", ex.getCode())
                    .addKeyValue("keel.duration_ms", elapsedMillis(start))
                    .log("Caso de uso {}: rechazado ({})", operation, ex.getCode());
            throw ex;
        } catch (RuntimeException | Error ex) {
            log.atError()
                    .addKeyValue("keel.operation", operation)
                    .addKeyValue("keel.outcome", "error")
                    .addKeyValue("keel.error_type", ex.getClass().getName())
                    .addKeyValue("keel.duration_ms", elapsedMillis(start))
                    .log("Caso de uso {}: falló con {}", operation, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Baja cardinalidad a propósito: el nombre de la clase del mensaje, nunca sus valores. */
    private Observation observationOf(String operation) {
        return Observation.createNotStarted("keel.use-case", observationRegistry)
                .contextualName(operation)
                .lowCardinalityKeyValue("keel.operation", operation);
    }

    /** Intentos de una transacción de escritura que pierde un conflicto transitorio. */
    private static final int WRITE_CONFLICT_ATTEMPTS = 3;

    /**
     * Reintenta la transacción de escritura cuando pierde un conflicto de escritura TRANSITORIO de
     * un motor relacional: un interbloqueo entre dos transacciones que toman filas en orden
     * inverso, y el motor aborta a una de las dos. En el reintento ve lo que la otra confirmó y falla con el error que el diseño declara
     * (la unicidad, el bloqueo optimista), o pasa. Agotados los intentos, sale como conflicto de
     * concurrencia (409).
     *
     * <p>El reintento repite el handler ENTERO, así que dentro de la transacción no puede haber
     * efectos que salgan del proceso (llamar a un proveedor, mandar un correo): la constitución ya lo
     * prohíbe, y aquí se vería como un efecto duplicado.
     *
     * <p>Dentro de una transacción AJENA no se reintenta: la del llamante ya quedó marcada para
     * revertir, y repetir el handler en ella solo cambiaría el error por otro más confuso.
     */
    private <T> T retryingWriteConflicts(Supplier<T> transaction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return transaction.get();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.get();
            } catch (RuntimeException ex) {
                if (!isTransientWriteConflict(ex)) {
                    throw ex;
                }
                if (attempt >= WRITE_CONFLICT_ATTEMPTS) {
                    throw new ObjectOptimisticLockingFailureException(
                            "Conflicto de escritura concurrente tras " + attempt + " intentos", ex);
                }
                log.atDebug().addKeyValue("keel.attempt", attempt)
                        .log("Conflicto de escritura transitorio: se reintenta la transacción");
                pause(attempt);
            }
        }
    }

    /**
     * El interbloqueo y la espera de bloqueo agotada, que Spring traduce a
     * PessimisticLockingFailureException en los cinco motores. Se busca en la CADENA de causas porque
     * al confirmar la transacción el error puede llegar envuelto. Ni el conflicto de @Version
     * (OptimisticLockingFailureException, otra rama de la jerarquía) ni el tope de transacción
     * (QueryTimeoutException) caen aquí: ninguno se arregla repitiendo.
     *
     * <p>Visible en el paquete para que store-check lo mida contra el error que da el motor.
     */
    static boolean isTransientWriteConflict(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(10L * attempt + ThreadLocalRandom.current().nextLong(10));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se reintentaba una transacción", interrupted);
        }
    }
}
