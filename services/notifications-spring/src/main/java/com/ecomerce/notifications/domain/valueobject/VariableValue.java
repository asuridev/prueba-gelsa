package com.ecomerce.notifications.domain.valueobject;

import java.util.regex.Pattern;

/**
 * Valor que el llamante da a una variable declarada al pedir un envío.
 */
public record VariableValue(String name, String value) {

    private static final Pattern NAME_FORMAT = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    public VariableValue {
        if (name == null) {
            throw new IllegalArgumentException("VariableValue.name es obligatorio");
        }
        if (!NAME_FORMAT.matcher(name).matches()) {
            throw new IllegalArgumentException("VariableValue.name no cumple el formato declarado por su tipo");
        }
        if (value == null) {
            throw new IllegalArgumentException("VariableValue.value es obligatorio");
        }
        if (value.length() > 10000) {
            throw new IllegalArgumentException("VariableValue.value es más largo que el máximo declarado por su tipo (10000)");
        }
    }
}
