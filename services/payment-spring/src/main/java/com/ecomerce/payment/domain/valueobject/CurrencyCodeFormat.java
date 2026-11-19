package com.ecomerce.payment.domain.valueobject;

import com.ecomerce.payment.domain.errors.ValueFormatException;
import java.util.regex.Pattern;

/**
 * Formato declarado por el value type CurrencyCode: Código de moneda ISO 4217 en mayúsculas.
 */
public final class CurrencyCodeFormat {

    // La regex del diseño, en un único sitio: quien la vuelva a compilar a mano crea
    // una segunda definición que nadie sincroniza.
    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{3}$");

    private CurrencyCodeFormat() {
    }

    /**
     * Hace cumplir el formato en el dominio: se llama en el factory o el método de
     * negocio de la entidad que recibe el valor. La entrada HTTP ya lo validó (un 400
     * antes que cualquier error de negocio); esto cubre lo que no entra por la API,
     * como un evento o una operación interna.
     *
     * No aplica a null/blank: la presencia la decide la regla de negocio.
     */
    public static void validate(String value) {
        if (!matches(value)) {
            throw new ValueFormatException("El valor no cumple el formato declarado por CurrencyCode");
        }
    }

    /** El mismo juicio sin lanzar, para quien tenga que decidir en vez de rechazar. */
    public static boolean matches(String value) {
        return value == null || value.isBlank() || FORMAT.matcher(value).matches();
    }
}
