package com.ecomerce.notifications.domain.valueobject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lectura de los marcadores {@code {{name}}} de un texto de plantilla, tal como los define el diseño:
 * tras {@code {{} vienen cero o más espacios (solo U+0020), un VariableName, cero o más espacios y
 * {@code }}}. El texto se lee de izquierda a derecha y solo {@code {{} abre un marcador: un {@code }}}
 * fuera de un marcador es texto literal. Cualquier otra forma tras un {@code {{} (incluida la triple
 * llave) es un marcador mal formado.
 */
public final class TemplateMarkers {

    private static final int MAX_NAME_LENGTH = 64;

    private TemplateMarkers() {
    }

    /**
     * Resultado de leer un texto.
     *
     * @param wellFormed true si toda aparición de {@code {{} abre un marcador bien formado
     * @param names      nombres de los marcadores bien formados leídos, en orden y con repeticiones
     */
    public record Scan(boolean wellFormed, List<String> names) {
    }

    public static Scan scan(String text) {
        List<String> names = new ArrayList<>();
        if (text == null) {
            return new Scan(true, names);
        }
        int n = text.length();
        int from = 0;
        while (true) {
            int open = text.indexOf("{{", from);
            if (open < 0) {
                return new Scan(true, names);
            }
            int p = open + 2;
            while (p < n && text.charAt(p) == ' ') {
                p++;
            }
            int start = p;
            if (p >= n || !isLetter(text.charAt(p))) {
                return new Scan(false, names);
            }
            p++;
            while (p < n && p - start < MAX_NAME_LENGTH && isNameChar(text.charAt(p))) {
                p++;
            }
            String name = text.substring(start, p);
            while (p < n && text.charAt(p) == ' ') {
                p++;
            }
            if (!text.startsWith("}}", p)) {
                return new Scan(false, names);
            }
            names.add(name);
            from = p + 2;
        }
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isNameChar(char c) {
        return isLetter(c) || (c >= '0' && c <= '9') || c == '_';
    }
}
