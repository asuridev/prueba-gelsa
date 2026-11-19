package com.ecomerce.order.application.support;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executors que llevan el contexto del hilo que lanza la tarea al hilo que la ejecuta.
 *
 * <p>Úsalo SIEMPRE en vez de {@code Executors.newVirtualThreadPerTaskExecutor()}: el MDC
 * (correlationId, y con telemetría traceId/spanId) y la observación activa son ThreadLocal, y un
 * executor normal no los copia. Sin esto, los logs de una tarea paralela salen sin correlación y
 * sus spans quedan huérfanos, separados de la traza de la petición. Lo vigila
 * {@code infra/check-logging.sh}.
 *
 * <pre>{@code
 * try (ExecutorService exec = ContextPropagatingExecutors.newVirtualThreadPerTaskExecutor()) {
 *     Future<A> a = exec.submit(() -> port.findA(id));
 *     Future<B> b = exec.submit(() -> port.findB(id));
 *     ...
 * }
 * }</pre>
 */
public final class ContextPropagatingExecutors {

    static {
        // El MDC entero, no una lista de claves: así viajan también las que añada la telemetría.
        // La observación activa ya la registra micrometer-observation por ServiceLoader, y el
        // correlationId de CorrelationContext lo registra ContextPropagationConfig al arrancar
        // (vive en infrastructure, que esta capa no puede importar). Registrar el MDC también
        // aquí deja el helper usable fuera de un contexto de Spring; repetirlo no duplica nada.
        ContextRegistry.getInstance().registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());
    }

    private static final ContextSnapshotFactory SNAPSHOTS = ContextSnapshotFactory.builder().build();

    private ContextPropagatingExecutors() {
        // Clase de utilidad.
    }

    /** Un hilo virtual por tarea, con el contexto del hilo que la lanza. */
    public static ExecutorService newVirtualThreadPerTaskExecutor() {
        return ContextExecutorService.wrap(Executors.newVirtualThreadPerTaskExecutor(), () -> SNAPSHOTS.captureAll());
    }
}
