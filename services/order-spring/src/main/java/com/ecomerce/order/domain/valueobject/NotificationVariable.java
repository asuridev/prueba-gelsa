package com.ecomerce.order.domain.valueobject;

/**
 * Par nombre-valor que rellena una variable de la plantilla de correo en notifications.
 */
public record NotificationVariable(String name, String value) {

    public NotificationVariable {
        if (name == null) {
            throw new IllegalArgumentException("NotificationVariable.name es obligatorio");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("NotificationVariable.name es más largo que el máximo declarado por su tipo (100)");
        }
        if (value == null) {
            throw new IllegalArgumentException("NotificationVariable.value es obligatorio");
        }
        if (value.length() > 10000) {
            throw new IllegalArgumentException("NotificationVariable.value es más largo que el máximo declarado por su tipo (10000)");
        }
    }
}
