package com.ecomerce.notifications.domain.valueobject;

import java.util.regex.Pattern;

/**
 * Variable declarada por una versión de plantilla.
 */
public record TemplateVariable(String name, String description, Boolean required) {

    private static final Pattern NAME_FORMAT = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    public TemplateVariable {
        if (name == null) {
            throw new IllegalArgumentException("TemplateVariable.name es obligatorio");
        }
        if (!NAME_FORMAT.matcher(name).matches()) {
            throw new IllegalArgumentException("TemplateVariable.name no cumple el formato declarado por su tipo");
        }
        if (description == null) {
            throw new IllegalArgumentException("TemplateVariable.description es obligatorio");
        }
        if (description.length() > 500) {
            throw new IllegalArgumentException("TemplateVariable.description es más largo que el máximo declarado por su tipo (500)");
        }
        if (required == null) {
            throw new IllegalArgumentException("TemplateVariable.required es obligatorio");
        }
    }
}
