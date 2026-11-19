package com.ecomerce.order.domain.valueobject;

import com.ecomerce.order.domain.errors.ValueFormatException;
import java.util.regex.Pattern;

/**
 * Formato declarado por el value type EmailAddress: Dirección de correo electrónico del comprador, tal como la trae su token.
 */
public final class EmailAddressFormat {

    // La regex del diseño, en un único sitio: quien la vuelva a compilar a mano crea
    // una segunda definición que nadie sincroniza.
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmailAddressFormat() {
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
            throw new ValueFormatException("El valor no cumple el formato declarado por EmailAddress");
        }
    }

    /** El mismo juicio sin lanzar, para quien tenga que decidir en vez de rechazar. */
    public static boolean matches(String value) {
        return value == null || value.isBlank() || FORMAT.matcher(value).matches();
    }
}
