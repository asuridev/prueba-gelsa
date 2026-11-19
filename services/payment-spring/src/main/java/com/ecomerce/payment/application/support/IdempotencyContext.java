package com.ecomerce.payment.application.support;

import java.util.Optional;

/**
 * Clave de idempotencia de la petición en curso.
 *
 * La cabecera es transporte, así que no viaja dentro del Command: el Command es
 * el cuerpo HTTP y Jackson lo deserializa entero desde ahí — un componente más
 * sería settable desde el cuerpo. Se resuelve como el contexto de correlación:
 * un ThreadLocal que abre el filtro de entrada y lee el handler.
 *
 * Los hilos son de un pool y se reutilizan: quien abre el contexto SIEMPRE debe
 * cerrarlo, o la siguiente petición atendida por ese hilo heredaría una clave
 * ajena y se saltaría su propia ejecución.
 *
 * La cabecera es OPCIONAL: si el cliente no la manda, get() devuelve un Optional
 * vacío y la operación se ejecuta sin deduplicar — no se rechaza, salvo que el
 * diseño declare el error que la exige (ver conventions/mapping.md).
 */
public final class IdempotencyContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private IdempotencyContext() {
        // Clase de utilidad.
    }

    /** Fija la clave del hilo actual; no hace nada si viene nula o en blanco. */
    public static void set(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        CURRENT.set(idempotencyKey);
    }

    /** @return la clave de la petición en curso, vacío si el cliente no la envió. */
    public static Optional<String> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Cierra el contexto. Va siempre en un finally. */
    public static void clear() {
        CURRENT.remove();
    }
}
