package com.ecomerce.payment.infrastructure.messaging.outbox;

/**
 * Una fila nueva en el outbox. La publica el bridge al escribirla, DENTRO de la transacción del cambio, y el relay de
 * esta réplica la oye después del commit ({@code @TransactionalEventListener}): es lo que hace que el evento salga en
 * milisegundos en vez de esperar al sondeo de respaldo. Si la transacción revierte, no llega a nadie.
 */
public record OutboxAppended() {

    public static final OutboxAppended INSTANCE = new OutboxAppended();
}
