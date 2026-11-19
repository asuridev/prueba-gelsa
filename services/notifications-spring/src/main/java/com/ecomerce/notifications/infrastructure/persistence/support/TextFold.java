package com.ecomerce.notifications.infrastructure.persistence.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pliega un texto para compararlo como lo declara el diseño (`compare` de domain.keel.yaml).
 *
 * <p>Lo usan el adaptador de persistencia, que estampa la columna sombra
 * {@code <campo>Normalized} al guardar, y el filtro que la consulta: los dos TIENEN que plegar
 * con la misma función, o el filtro deja de casar en silencio.
 *
 * <p>{@link Locale#ROOT} a propósito: con el locale por defecto de la JVM, en turco
 * {@code "I".toLowerCase()} es {@code "ı"} y dos réplicas con distinto locale plegarían
 * distinto el mismo valor.
 */
public final class TextFold {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private TextFold() {
        // Clase de utilidad.
    }

    /** compare: ignore-case — sin mayúsculas. Null queda null. */
    public static String foldCase(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** compare: ignore-case-accents — sin mayúsculas y sin marcas diacríticas. Null queda null. */
    public static String foldCaseAndAccents(String value) {
        if (value == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
