package com.ecomerce.payment.infrastructure.timer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El sondeador del cronómetro: UNO por réplica, en un hilo de plataforma propio (como el {@code SchedulingConfig}: lo
 * que corre por reloj no comparte carriers con la carga web).
 *
 * <p>Cada pasada reclama un lote de lo vencido ({@link TimerStore#claim}: dos réplicas nunca toman el mismo mientras
 * dure el arriendo), resuelve cada temporizador con su {@link TimerHandler} DENTRO de una transacción en la que también
 * lo borra o lo rearma, y duerme hasta el vencimiento más próximo, con un tope para ver lo que arman otras réplicas. Un
 * temporizador que esta réplica arma y vence antes la despierta ({@link TimerArmed}). Es al menos una vez: si la réplica
 * muere con un lote arrendado, otra lo retoma al vencer el arriendo, y el efecto es uno solo porque la transición del
 * plazo es condicional y la revalidación descarta lo que ya no aplica.
 *
 * <p>Se apaga con {@code scheduling.enabled=false} (el perfil {@code test} lo apaga): los temporizadores se siguen
 * ARMANDO, que es persistencia; lo que deja de haber es quien los dispare.
 */
@Component
@ConditionalOnProperty(prefix = "scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TimerPoller implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TimerPoller.class);

    private final TimerStore store;
    private final Map<String, TimerHandler> handlers;
    private final Map<String, RecurringTimerHandler> recurrings;
    private final List<String> kinds;
    private final TransactionTemplate transaction;
    private final Object monitor = new Object();
    private ObservationRegistry observations = ObservationRegistry.NOOP;

    @Value("${timer.max-sleep-ms:30000}")
    private long maxSleepMs;

    @Value("${timer.batch-size:50}")
    private int batchSize;

    @Value("${timer.lease-ms:120000}")
    private long leaseMs;

    @Value("${timer.max-attempts:10}")
    private int maxAttempts;

    @Value("${timer.retry-delay-ms:30000}")
    private long retryDelayMs;

    private volatile boolean running;
    private volatile long wakeAtMs = Long.MAX_VALUE;
    private volatile CountDownLatch stopped;

    /** Sin plazos no hay ningún {@link TimerHandler}: por eso los dos van por {@link ObjectProvider} y no por lista. */
    @Autowired
    public TimerPoller(
            TimerStore store,
            ObjectProvider<TimerHandler> handlers,
            ObjectProvider<RecurringTimerHandler> recurrings,
            PlatformTransactionManager transactionManager,
            ObservationRegistry observations,
            MeterRegistry meters) {
        this(store, handlers.orderedStream().toList(), recurrings.orderedStream().toList(), transactionManager);
        this.observations = observations;
        registerGauges(meters);
    }

    /** El de las pruebas: los handlers a mano, como dos réplicas con los suyos. */
    TimerPoller(
            TimerStore store,
            List<TimerHandler> handlers,
            List<RecurringTimerHandler> recurrings,
            PlatformTransactionManager transactionManager) {
        this.store = store;
        this.handlers = handlers.stream().collect(Collectors.toMap(TimerHandler::kind, Function.identity()));
        this.recurrings = recurrings.stream().collect(Collectors.toMap(RecurringTimerHandler::kind, Function.identity()));
        // Solo los kinds que esta réplica conoce (R4): un temporizador de otra versión no lo toca, ni para borrarlo.
        this.kinds = Stream.concat(this.handlers.keySet().stream(), this.recurrings.keySet().stream()).toList();
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Las réplicas aseguran sus recurrentes al arrancar (R1), con el siguiente tick desde la hora de la base. */
    void ensureRecurring() {
        Instant now = store.dbNow();
        for (RecurringTimerHandler recurring : recurrings.values()) {
            store.ensureRecurring(recurring.kind(), recurring.spec(), nextTick(recurring.spec(), now));
        }
    }

    /** El siguiente tick ESTRICTAMENTE posterior a {@code from}, con el cron en UTC (T7). */
    static Instant nextTick(String spec, Instant from) {
        return CronExpression.parse(spec).next(from.atZone(ZoneOffset.UTC)).toInstant();
    }

    /** Cuánto dormir: hasta lo próximo, con el tope y nunca negativo. */
    static long sleepMs(Long nextDueMs, long maxSleepMs) {
        if (nextDueMs == null) {
            return maxSleepMs;
        }
        return Math.max(0, Math.min(nextDueMs, maxSleepMs));
    }

    /**
     * Una pasada: reclama, resuelve y devuelve cuánto dormir. Visible para las pruebas, que la ejecutan con un almacén
     * en memoria y la hora en su mano (DL-10).
     */
    long pollOnce() {
        UUID token = UUID.randomUUID();
        List<ClaimedTimer> claimed = store.claim(kinds, batchSize, leaseMs, token);
        for (ClaimedTimer timer : claimed) {
            RecurringTimerHandler recurring = recurrings.get(timer.kind());
            if (recurring != null) {
                tick(recurring, timer, token);
                continue;
            }
            TimerHandler handler = handlers.get(timer.kind());
            try {
                if (handler.inTransaction()) {
                    observed(timer.kind(), () -> transaction.executeWithoutResult(status -> settle(timer, token, handler.handle(timer, store.dbNow()))));
                } else {
                    observed(timer.kind(), () -> settle(timer, token, handler.handle(timer, store.dbNow())));
                }
            } catch (RuntimeException failure) {
                log.warn("El temporizador {} ({}) falló en su intento {}: {}", timer.id(), timer.kind(), timer.attempts() + 1, failure.toString());
                store.fail(timer.id(), token, maxAttempts, retryDelayMs);
                if (timer.attempts() + 1 >= maxAttempts) {
                    log.error("El temporizador {} ({}) agotó sus {} intentos y queda APARCADO", timer.id(), timer.kind(), maxAttempts);
                }
            }
        }
        return sleepMs(store.nextDueMs(kinds), maxSleepMs);
    }

    /** Borra el temporizador o lo rearma, con la valla con la que se reclamó. */
    private void settle(ClaimedTimer timer, UUID token, TimerOutcome outcome) {
        if (outcome.done()) {
            store.complete(timer.id(), token);
        } else {
            store.reschedule(timer.id(), token, outcome.dueAt());
        }
    }

    /**
     * Un tick de un recurrente en modo clúster: lo ejecuta esta réplica porque ganó la fila, FUERA de toda transacción
     * (el mecanismo gestiona las suyas), y lo rearma con el siguiente tick desde la hora de la base de DESPUÉS (R2). Si
     * otra réplica le quitó la fila (el tick duró más que el arriendo), el rearme no casa con la valla y no pisa nada.
     *
     * <p>Un tick que falla NO se aparca, a diferencia de un plazo: se rearma igual para su siguiente tick, como hacía su
     * {@code @Scheduled}. Aparcarlo tras {@code max-attempts} fallos —una base caída unos minutos— dejaría el barrido o
     * la purga sin reloj para siempre, y nada lo volvería a armar: asegurar al arrancar no toca una fila con el mismo spec.
     */
    private void tick(RecurringTimerHandler recurring, ClaimedTimer timer, UUID token) {
        try {
            observed(timer.kind(), recurring::tick);
        } catch (RuntimeException failure) {
            log.warn("El tick del recurrente {} falló; se rearma para el siguiente: {}", timer.kind(), failure.toString());
        }
        store.reschedule(timer.id(), token, nextTick(recurring.spec(), store.dbNow()));
    }

    /**
     * Un disparo, observado: {@code keel.timer.tick} con el {@code kind} (acotado por el diseño, nunca el id de la entidad) y el
     * desenlace. Es raíz a propósito: un tick no tiene petición de la que colgar, y abre la traza de lo que hace.
     */
    private void observed(String kind, Runnable work) {
        Observation observation = Observation.createNotStarted(TICK, observations)
                .lowCardinalityKeyValue("keel.timer.kind", kind)
                .start();
        try (Observation.Scope scope = observation.openScope()) {
            work.run();
            observation.lowCardinalityKeyValue("keel.outcome", "ok");
        } catch (RuntimeException failure) {
            observation.lowCardinalityKeyValue("keel.outcome", "error");
            observation.error(failure);
            throw failure;
        } finally {
            observation.stop();
        }
    }

    private static final String TICK = "keel.timer.tick";

    /**
     * El RETRASO y los APARCADOS. Gauges y no contadores: lo que importa es cuánto lleva esperando lo vencido AHORA y
     * cuántos plazos hay rendidos, también los de antes de arrancar. Cada réplica publica lo mismo (la tabla es una): el
     * panel los agrega con {@code max}.
     */
    private void registerGauges(MeterRegistry meters) {
        Gauge.builder("keel.timer.lag.seconds", this, TimerPoller::lagSeconds)
                .description("Segundos que lleva esperando el temporizador vencido y sin reclamar más antiguo")
                .register(meters);
        Gauge.builder("keel.timer.parked", store, TimerStore::parkedCount)
                .description("Temporizadores que agotaron sus intentos y quedaron aparcados")
                .register(meters);
    }

    private double lagSeconds() {
        Long ms = store.lagMs(kinds);
        return ms == null ? 0 : Math.max(0, ms) / 1000.0;
    }

    /** Un temporizador armado en ESTA réplica: si vence antes de lo que iba a dormir, despierta (tras el commit). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onArmed(TimerArmed armed) {
        synchronized (monitor) {
            if (armed.dueAt().toEpochMilli() < wakeAtMs) {
                wakeAtMs = armed.dueAt().toEpochMilli();
                monitor.notifyAll();
            }
        }
    }

    /** Cuándo piensa despertar (milisegundos de la época, según el reloj de la instancia). */
    long wakeAtMs() {
        return wakeAtMs;
    }

    /** Fija cuándo despertar tras una pasada; lo usa el bucle y lo comprueban las pruebas. */
    void planWake(long nowMs, long sleepMs) {
        synchronized (monitor) {
            wakeAtMs = nowMs + sleepMs;
        }
    }

    private void loop() {
        try {
            ensureRecurring();
        } catch (RuntimeException failure) {
            log.error("El cronómetro no pudo asegurar sus recurrentes; lo reintenta en la siguiente pasada", failure);
        }
        while (running) {
            long sleep;
            try {
                sleep = pollOnce();
            } catch (RuntimeException failure) {
                log.error("La pasada del cronómetro falló; se reintenta tras el sueño máximo", failure);
                sleep = maxSleepMs;
            }
            planWake(System.currentTimeMillis(), sleep);
            synchronized (monitor) {
                long remaining;
                while (running && (remaining = wakeAtMs - System.currentTimeMillis()) > 0) {
                    try {
                        monitor.wait(remaining);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * El hilo lo da un ejecutor de Spring con hilos de PLATAFORMA, no un {@code Thread} a mano (lo veta
     * check-logging.sh). Empieza sin contexto a propósito, como un {@code @Scheduled}: cada disparo abre el suyo.
     */
    @Override
    public void start() {
        running = true;
        stopped = new CountDownLatch(1);
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("payment-timer-");
        executor.setVirtualThreads(false);
        executor.execute(() -> {
            try {
                loop();
            } finally {
                stopped.countDown();
            }
        });
    }

    @Override
    public void stop() {
        running = false;
        synchronized (monitor) {
            monitor.notifyAll();
        }
        // Se espera a la pasada en vuelo: cortarla deja temporizadores arrendados sin hacer hasta que venza el arriendo.
        if (stopped != null) {
            try {
                stopped.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
