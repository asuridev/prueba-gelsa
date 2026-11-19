package com.ecomerce.notifications.domain.errors;

/**
 * El asunto renderizado queda vacío o solo con espacios (p. ej. un subject que es solo el marcador de una variable opcional que no llega); no se acepta un correo sin asunto.
 */
public class EmptyRenderedSubjectError extends BusinessException {

    public EmptyRenderedSubjectError(String message) {
        super(message, "EMPTY_RENDERED_SUBJECT", 422, null);
    }
}
