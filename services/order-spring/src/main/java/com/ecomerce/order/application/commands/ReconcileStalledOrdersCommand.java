package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;

/**
 * Rescata los pedidos que se quedaron esperando un desenlace de payment - los que nunca llegaron a registrarse como cobro, los autorizados cuya captura no se confirmó y los que esperan una anulación.
 */
public record ReconcileStalledOrdersCommand() implements Command {
}
