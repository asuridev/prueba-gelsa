package com.ecomerce.order.infrastructure.configurations.concurrency;

import com.ecomerce.order.infrastructure.correlation.CorrelationContext;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Qué viaja del hilo que lanza un trabajo al hilo que lo ejecuta.
 *
 * <p>El MDC (correlationId, y con telemetría traceId/spanId), la observación activa —que es la
 * traza— y el correlationId de CorrelationContext son ThreadLocal: un hilo nuevo, virtual o no,
 * empieza sin ellos. Aquí se registran en el {@code ContextRegistry} de Micrometer AL ARRANCAR
 * (la observación la registra micrometer-observation por ServiceLoader), y de ese registro leen
 * los dos caminos por los que el código lanza trabajo a otro hilo:
 *
 * <ul>
 *   <li>{@code ContextPropagatingExecutors} ({@code application/support}), para paralelizar dentro
 *       de un handler;</li>
 *   <li>el executor de {@code @Async} de Boot, al que Boot aplica el {@link TaskDecorator} de abajo.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class ContextPropagationConfig {

    static {
        ContextRegistry registry = ContextRegistry.getInstance();
        // El MDC entero, no una lista de claves: así viajan también las que añada la telemetría.
        registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());
        // El correlationId de CorrelationContext, que no es el del MDC: es el que estampa un evento
        // al publicarse. Restaurarlo con set() también lo pone en el MDC; limpiarlo con clear()
        // lo quita de los dos.
        registry.registerThreadLocalAccessor("keel.correlation-id",
                CorrelationContext::get, CorrelationContext::set, CorrelationContext::clear);
    }

    /**
     * Captura el contexto al ENCOLAR la tarea y lo restaura al ejecutarla. Boot lo aplica a su
     * executor de tareas, así que un {@code @Async} hereda traza, MDC y correlación sin que el
     * código haga nada.
     */
    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
