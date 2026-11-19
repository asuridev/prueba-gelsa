package com.ecomerce.payment.domain.valueobject;

import java.util.regex.Pattern;

/**
 * Resumen visible de una tarjeta guardada; nunca incluye el número completo.
 */
public record CardDetails(String brand, String last4, Integer expMonth, Integer expYear) {

    private static final Pattern LAST4_FORMAT = Pattern.compile("^[0-9]{4}$");

    public CardDetails {
        if (brand == null) {
            throw new IllegalArgumentException("CardDetails.brand es obligatorio");
        }
        if (brand.length() > 30) {
            throw new IllegalArgumentException("CardDetails.brand es más largo que el máximo declarado por su tipo (30)");
        }
        if (last4 == null) {
            throw new IllegalArgumentException("CardDetails.last4 es obligatorio");
        }
        if (!LAST4_FORMAT.matcher(last4).matches()) {
            throw new IllegalArgumentException("CardDetails.last4 no cumple el formato declarado por su tipo");
        }
        if (expMonth == null) {
            throw new IllegalArgumentException("CardDetails.expMonth es obligatorio");
        }
        if (expMonth < 1) {
            throw new IllegalArgumentException("CardDetails.expMonth es menor que el mínimo declarado por su tipo (1)");
        }
        if (expMonth > 12) {
            throw new IllegalArgumentException("CardDetails.expMonth es mayor que el máximo declarado por su tipo (12)");
        }
        if (expYear == null) {
            throw new IllegalArgumentException("CardDetails.expYear es obligatorio");
        }
        if (expYear < 2000) {
            throw new IllegalArgumentException("CardDetails.expYear es menor que el mínimo declarado por su tipo (2000)");
        }
        if (expYear > 2100) {
            throw new IllegalArgumentException("CardDetails.expYear es mayor que el máximo declarado por su tipo (2100)");
        }
    }
}
