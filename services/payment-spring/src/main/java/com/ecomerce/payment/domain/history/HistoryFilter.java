package com.ecomerce.payment.domain.history;

import java.time.Instant;

/**
 * Los filtros de una lectura del historial (D10), los tres opcionales y combinados con AND: {@code field} casa la ruta
 * entera o el campo de la raíz que la contiene ({@code price} casa {@code price.amount}); {@code from} y {@code to}
 * incluyen su extremo.
 */
public record HistoryFilter(String field, Instant from, Instant to) {

    /** Sin filtros: el historial entero. */
    public static HistoryFilter none() {
        return new HistoryFilter(null, null, null);
    }
}
