package com.ecomerce.notifications.infrastructure.scheduling;

import com.ecomerce.notifications.application.commands.DispatchQueuedMessagesCommand;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.notifications.infrastructure.correlation.CorrelationContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Disparadores por reloj de las operaciones que declaran `schedule`.
 *
 * <p><strong>Los dispara el cronómetro</strong>: cada operación tiene su {@code <Operación>ScheduleTimer}
 * en infrastructure/timer, un recurrente de {@code keel_timer} que ejecuta UNA réplica por tick
 * —la que gana su fila— y que repite la pasada mientras el lote salga lleno. Este método solo
 * despacha. El tick es <em>al menos una vez</em> (si la réplica muere a mitad, otra lo repite al
 * vencer el arriendo), así que el handler de una operación que ACTÚA sobre lo que encuentra
 * sigue reclamando sus candidatos en vez de solo leerlos — el patrón está en
 * docs/keel/conventions/dependencies.md.
 *
 * <p><strong>No todos los métodos despachan igual, y no es un descuido.</strong> Un barrido
 * que reconcilia una activación saliente va por {@code dispatchWithoutTransaction}: su
 * garantía es un ORDEN de commits (reclamar y confirmar, llamar al proveedor fuera de toda
 * transacción, confirmar el desenlace), y una transacción abarcadora la rompe entera. Lo
 * mismo todo barrido con un reclamo generado: sus filas ya están confirmadas como tomadas, y
 * un fallo en una no puede revertir las demás. El resto —purgas, cierres— va por {@code dispatch} y corre en una transacción única, que es
 * lo correcto cuando no hay ninguna llamada a un tercero en medio. Quién usa cuál lo decide
 * el diseño (`dependencies.activations.<a>.reconciledBy`), no esta clase.
 *
 * <p><strong>El campo de segundos del cron no es 0 por casualidad.</strong> El diseño
 * declara cinco campos y build añade el sexto, repartiendo el arranque dentro del minuto
 * para que dos operaciones con la misma cadencia no salgan a la vez: lo que se amontona
 * cuando coinciden no son las consultas —el reclamo es corto— sino las llamadas a los
 * proveedores. La cadencia declarada no cambia; solo en qué segundo del minuto cae.
 * Igualarlos a 0 «por limpieza» deshace ese reparto.
 */
@Component
public class EmailMessageScheduler {

    private final UseCaseMediator mediator;

    public EmailMessageScheduler(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Despachado <b>sin transacción abarcadora</b> a propósito: este barrido confirma cada reclamo en su propia transacción y actúa sobre las filas una a una
     * EN MEDIO de su trabajo, así que su garantía es un orden de commits —reclamar y confirmar, actuar
     * fuera de toda transacción, confirmar el desenlace— y no una transacción única. Igualarlo a
     * {@code mediator.dispatch(...)} «por coherencia» con los demás métodos de esta clase deja el reclamo
     * sin confirmar hasta el final del lote: ninguna réplica lo ve y todas actúan sobre los mismos
     * candidatos.
     */
    // Lo dispara DispatchQueuedMessagesScheduleTimer (el cronómetro): una réplica por tick, con drenaje.
    public void dispatchQueuedMessages() {
        // Una pasada del barrido es una TRAZA nueva: no la origina ninguna petición, así que
        // aquí nace su correlación. Sin esto el hilo del @Scheduled no tiene ninguna abierta
        // y todo lo que publique viaja con correlationId nulo — el reloj es el único de los
        // tres puntos de entrada (filtro HTTP, listener del broker, esto) que no la establecía,
        // y su rastro era justo el que no se podía seguir de punta a punta.
        CorrelationContext.runWith(UUID.randomUUID().toString(), () -> mediator.dispatchWithoutTransaction(new DispatchQueuedMessagesCommand()));
    }
}
