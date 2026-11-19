package com.ecomerce.order.domain.clients;

/**
 * payment no pudo atender la llamada: no contestó (timeout, conexión, 5xx, circuito abierto), no se
 * pudo obtener el token M2M o nos rechazó con 401/403.
 *
 * <p>Es el resultado degradado de las activaciones con {@code onFailure: degrade}: lo lanza el
 * adaptador y lo decide el caso de uso (el pedido sigue en su estado y el barrido reintenta).
 * No es un {@code DomainException} a propósito: no es un rechazo de negocio y no debe salir por
 * la API como un 4xx.
 */
public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
