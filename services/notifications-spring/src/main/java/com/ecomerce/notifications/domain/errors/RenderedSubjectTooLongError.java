package com.ecomerce.notifications.domain.errors;

/**
 * El asunto renderizado pasa de 300 caracteres; no se recorta y no se acepta el mensaje.
 */
public class RenderedSubjectTooLongError extends BusinessException {

    public RenderedSubjectTooLongError(String message) {
        super(message, "RENDERED_SUBJECT_TOO_LONG", 422, null);
    }
}
