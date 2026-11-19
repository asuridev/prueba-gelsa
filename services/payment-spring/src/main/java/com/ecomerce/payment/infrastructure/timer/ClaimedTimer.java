package com.ecomerce.payment.infrastructure.timer;

import java.time.Instant;
import java.util.UUID;

/**
 * Un temporizador que ESTA réplica reclamó: lo es mientras dure su arriendo, y todo lo que se haga con él lleva la valla
 * ({@code token}) con la que se reclamó.
 *
 * @param id       su identidad en la tabla (el uuid, o el {@code _id} documental)
 * @param kind     qué es: un plazo o un recurrente ({@link TimerKinds})
 * @param entityId la entidad de un plazo; el uuid nulo en un recurrente
 * @param dueAt    cuándo vencía
 * @param attempts cuántas veces falló ya
 */
public record ClaimedTimer(String id, String kind, UUID entityId, Instant dueAt, int attempts) {
}
