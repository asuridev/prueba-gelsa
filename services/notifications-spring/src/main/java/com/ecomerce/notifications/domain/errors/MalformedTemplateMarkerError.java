package com.ecomerce.notifications.domain.errors;

/**
 * subject, htmlBody o textBody contienen un {{ que no abre un marcador {{name}} bien formado, como la triple llave {{{name}}}.
 */
public class MalformedTemplateMarkerError extends BusinessException {

    public MalformedTemplateMarkerError(String message) {
        super(message, "MALFORMED_TEMPLATE_MARKER", 422, null);
    }
}
