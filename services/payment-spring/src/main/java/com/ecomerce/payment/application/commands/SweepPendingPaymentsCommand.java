package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.interfaces.Command;

/**
 * Consulta a la pasarela los cobros que llevan más de 15 minutos esperando un desenlace sin respuesta y aplica lo que diga.
 */
public record SweepPendingPaymentsCommand() implements Command {
}
