package com.ecomerce.payment.domain.history;

import java.util.regex.Pattern;

/**
 * Una ruta personal del historial de una raíz (DSL 2.28): el patrón de keel-core ({@code historyPersonalPatterns}) y si
 * el campo es obligatorio, que decide qué vale al anonimizar: el sustituto {@code anon-<id>} o null.
 */
public record PersonalPath(Pattern pattern, boolean required) {

    public static PersonalPath of(String regex, boolean required) {
        return new PersonalPath(Pattern.compile(regex), required);
    }

    public boolean matches(String field) {
        return pattern.matcher(field).matches();
    }
}
