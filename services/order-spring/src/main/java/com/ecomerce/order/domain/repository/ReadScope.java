package com.ecomerce.order.domain.repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qué filas ve quien lee (DSL 2.27, la lectura acotada de security.keel.yaml). Lo construye el borde desde el token
 * ({@code ReadScopes}) y lo estampa en el mensaje; el handler lo pasa al puerto y el ADAPTADOR lo aplica en la consulta,
 * en su total y en la carga por id. Desde otra puerta (un evento, el reloj) llega {@link #unrestricted()}.
 *
 * <ul>
 *   <li>{@link Unrestricted}: sin filtro. Solo lo da una exención.</li>
 *   <li>{@link OwnedBy}: el dueño es la identidad del llamante. Una identidad nula no ve nada.</li>
 *   <li>{@link Within}: el campo acotado está en el conjunto del claim. El conjunto vacío no ve nada.</li>
 * </ul>
 */
public sealed interface ReadScope {

    ReadScope UNRESTRICTED = new Unrestricted();

    record Unrestricted() implements ReadScope {
    }

    record OwnedBy(String value) implements ReadScope {
    }

    record Within(Set<String> values) implements ReadScope {

        public Within {
            values = Set.copyOf(values);
        }
    }

    static ReadScope unrestricted() {
        return UNRESTRICTED;
    }

    static ReadScope ownedBy(String value) {
        return new OwnedBy(value);
    }

    static ReadScope within(Collection<String> values) {
        return new Within(new HashSet<>(values));
    }

    /** ¿Ve este scope una fila cuyo valor acotado es {@code value}? */
    default boolean admits(String value) {
        if (this instanceof Unrestricted) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (this instanceof Within within) {
            return within.values().contains(value);
        }
        return value.equals(((OwnedBy) this).value());
    }

    /**
     * La parte del scope en la clave de una caché (D6): {@code all}, {@code by:<valor>}, {@code none} o
     * {@code in:<v1>,<v2>} ordenado, con la coma y la barra escapadas. Dos scopes distintos nunca comparten entrada.
     */
    default String cacheKey() {
        if (this instanceof OwnedBy owned) {
            return owned.value() == null ? "none" : "by:" + escape(owned.value());
        }
        if (this instanceof Within within) {
            return "in:" + within.values().stream().sorted().map(ReadScope::escape).collect(Collectors.joining(","));
        }
        return "all";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,");
    }
}
