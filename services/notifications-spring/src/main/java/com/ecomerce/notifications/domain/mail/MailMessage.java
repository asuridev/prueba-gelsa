package com.ecomerce.notifications.domain.mail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Un correo listo para salir: destinatarios, asunto y cuerpo ya renderizados.
 *
 * <p>Es un value object de DOMINIO y no un DTO de infraestructura, y la diferencia
 * importa: quien decide qué correo sale es el caso de uso, y tiene que poder
 * componerlo sin depender de jakarta.mail ni de Spring. El adaptador solo lo
 * traduce al protocolo.
 *
 * <p><b>El asunto se sanea en el constructor</b>, no en el adaptador. Un \r o un \n
 * dentro del asunto permite inyectar cabeceras SMTP —un Bcc: que nadie puso—, y
 * ponerlo aquí significa que <b>ningún</b> camino puede construir un mensaje con el
 * asunto sin sanear, ni siquiera uno que se escriba después y no pase por el
 * adaptador de hoy.
 *
 * @param from    remitente (sale de un dato del servicio)
 * @param replyTo dirección de respuesta, o null para que se responda al remitente
 * @param to      destinatarios
 * @param subject asunto ya interpolado
 * @param html    cuerpo HTML
 * @param text    cuerpo en texto plano
 * @param headers cabeceras propias (p. ej. un {@code X-…} con el id del envío, que el proveedor
 *                devuelve con el rebote), ya saneadas; vacío si no hay ninguna
 */
public record MailMessage(String from, String replyTo, List<String> to, List<String> cc, String subject, String html,
        String text, Map<String, String> headers) {

    /**
     * Cabeceras que este record ya gobierna con sus propios campos, o que compone el
     * transporte. Una cabecera propia con uno de estos nombres duplicaría o pisaría lo que el
     * resto del mensaje dice, así que se rechaza al construir.
     */
    private static final Set<String> RESERVED_HEADERS = Set.of(
            "from", "sender", "to", "cc", "bcc", "reply-to", "subject", "date", "message-id",
            "mime-version", "return-path");

    /** Nombre de cabecera (RFC 5322): imprimibles sin espacio ni dos puntos. */
    private static final Pattern HEADER_NAME = Pattern.compile("[!-9;-~]+");

    public MailMessage {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        subject = sanitizeSubject(subject);
        headers = sanitizeHeaders(headers);
    }

    /** Sin cabeceras propias: la forma habitual. */
    public MailMessage(String from, String replyTo, List<String> to, List<String> cc, String subject, String html,
            String text) {
        this(from, replyTo, to, cc, subject, html, text, Map.of());
    }

    /**
     * Quita los saltos de línea del asunto. No es cosmética: con ellos, una variable
     * interpolada puede cerrar la cabecera Subject: y abrir otra.
     */
    private static String sanitizeSubject(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", " ").trim();
    }

    /**
     * Las cabeceras propias se sanean AQUÍ y no en el adaptador, por lo mismo que el asunto: un
     * salto de línea en el valor abre otra cabecera, y ningún camino debe poder construir el
     * mensaje sin pasar por esto. El NOMBRE se valida en vez de sanearse: un nombre malformado o
     * reservado es un error de quien compone el mensaje, no un dato que haya que arreglar.
     */
    private static Map<String, String> sanitizeHeaders(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, String> clean = new LinkedHashMap<>();
        value.forEach((name, content) -> {
            if (name == null || !HEADER_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Nombre de cabecera no válido: " + name);
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (RESERVED_HEADERS.contains(lower) || lower.startsWith("content-")) {
                throw new IllegalArgumentException("La cabecera " + name + " la compone el propio mensaje");
            }
            clean.put(name, content == null ? "" : content.replaceAll("[\\r\\n]", " ").trim());
        });
        return Collections.unmodifiableMap(clean);
    }
}
