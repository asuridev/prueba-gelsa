package com.ecomerce.notifications.domain.mail;

/**
 * La plantilla no compila o el renderizado falla. Se distingue del fallo de entrega
 * a propósito: aquí no se ha llegado a hablar con el proveedor, y el problema es del
 * contenido, no del transporte.
 */
public class TemplateRenderException extends RuntimeException {

    public TemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
