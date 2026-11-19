package com.ecomerce.payment.domain.history;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/**
 * El diff de dos instantáneas de un agregado (D2, D3): la referencia ejecutable es {@code historyDiff} de keel-core, y
 * los cuatro generadores comparan lo mismo. Una instantánea es ruta del contrato → valor del cable; {@code before} null
 * es un alta. Devuelve solo lo que cambió, ordenado por ruta.
 */
public final class HistoryDiff {

    private HistoryDiff() {
    }

    public static List<FieldChange> diff(Map<String, JsonNode> before, Map<String, JsonNode> after) {
        TreeSet<String> paths = new TreeSet<>(after.keySet());
        if (before != null) {
            paths.addAll(before.keySet());
        }
        List<FieldChange> changes = new ArrayList<>();
        for (String path : paths) {
            JsonNode was = valueAt(before, path);
            JsonNode is = valueAt(after, path);
            if (!was.equals(is)) {
                changes.add(new FieldChange(path, was, is));
            }
        }
        return changes;
    }

    private static JsonNode valueAt(Map<String, JsonNode> snapshot, String path) {
        JsonNode value = snapshot == null ? null : snapshot.get(path);
        return value == null ? NullNode.getInstance() : value;
    }

    /** Un paso de una ruta con dot-path, sin reventar en un value object ausente. */
    public static <A, B> B at(A value, Function<A, B> step) {
        return value == null ? null : step.apply(value);
    }

    /** Las filas del filtro por campo de una entrada: su ruta y el campo de la raíz que la contiene, sin repetir. */
    public static Map<String, String> fieldRows(List<FieldChange> changes) {
        Map<String, String> rows = new LinkedHashMap<>();
        for (FieldChange change : changes) {
            rows.putIfAbsent(change.field(), change.field().split("[.\\[]", 2)[0]);
        }
        return rows;
    }
}
