package com.ecomerce.payment.domain.history;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.StringNode;

/**
 * La anonimización del historial (D8): las referencias ejecutables son {@code anonymizeHistoryChanges} y
 * {@code anonymizedEntryChanges} de keel-core. Un valor personal pasa a {@code anon-<id de la raíz>} si el campo es
 * obligatorio y a null si no, en {@code before} y en {@code after}: el anterior ya no existe en ninguna parte.
 */
public final class HistoryAnonymization {

    private HistoryAnonymization() {
    }

    /** Los cambios de una entrada, reescritos, y si alguno cambió. */
    public record Rewrite(List<FieldChange> changes, boolean touched) {
    }

    public static Rewrite rewrite(List<FieldChange> changes, List<PersonalPath> personal, String rootId) {
        boolean touched = false;
        List<FieldChange> out = new ArrayList<>();
        for (FieldChange change : changes) {
            PersonalPath match = match(personal, change.field());
            if (match == null) {
                out.add(change);
                continue;
            }
            JsonNode value = value(match, rootId);
            if (!value.equals(nullSafe(change.before())) || !value.equals(nullSafe(change.after()))) {
                touched = true;
            }
            out.add(new FieldChange(change.field(), value, value));
        }
        return new Rewrite(out, touched);
    }

    /** Los cambios de la entrada {@code anonymized}: una por ruta personal que aparece en el historial, por ruta. */
    public static List<FieldChange> anonymizedEntry(List<List<FieldChange>> entries, List<PersonalPath> personal, String rootId) {
        TreeMap<String, JsonNode> seen = new TreeMap<>();
        for (List<FieldChange> changes : entries) {
            for (FieldChange change : changes) {
                PersonalPath match = match(personal, change.field());
                if (match != null) {
                    seen.putIfAbsent(change.field(), value(match, rootId));
                }
            }
        }
        List<FieldChange> out = new ArrayList<>();
        seen.forEach((field, value) -> out.add(new FieldChange(field, value, value)));
        return out;
    }

    private static PersonalPath match(List<PersonalPath> personal, String field) {
        for (PersonalPath path : personal) {
            if (path.matches(field)) {
                return path;
            }
        }
        return null;
    }

    private static JsonNode value(PersonalPath path, String rootId) {
        return path.required() ? StringNode.valueOf("anon-" + rootId) : NullNode.getInstance();
    }

    private static JsonNode nullSafe(JsonNode value) {
        return value == null ? NullNode.getInstance() : value;
    }
}
