package com.ecomerce.notifications.domain.valueobject;

import java.util.regex.Pattern;

/**
 * Remitente congelado en el mensaje al aceptarlo; el envío nunca vuelve a leer la configuración viva. Es un buzón de la organización, no dato personal (ver SenderSettings).
 */
public record SenderSnapshot(String address, String name, String replyToAddress) {

    private static final Pattern ADDRESS_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern REPLY_TO_ADDRESS_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public SenderSnapshot {
        if (address == null) {
            throw new IllegalArgumentException("SenderSnapshot.address es obligatorio");
        }
        if (!ADDRESS_FORMAT.matcher(address).matches()) {
            throw new IllegalArgumentException("SenderSnapshot.address no cumple el formato declarado por su tipo");
        }
        if (address.length() > 254) {
            throw new IllegalArgumentException("SenderSnapshot.address es más largo que el máximo declarado por su tipo (254)");
        }
        if (name != null && name.length() > 120) {
            throw new IllegalArgumentException("SenderSnapshot.name es más largo que el máximo declarado por su tipo (120)");
        }
        if (replyToAddress != null && !REPLY_TO_ADDRESS_FORMAT.matcher(replyToAddress).matches()) {
            throw new IllegalArgumentException("SenderSnapshot.replyToAddress no cumple el formato declarado por su tipo");
        }
        if (replyToAddress != null && replyToAddress.length() > 254) {
            throw new IllegalArgumentException("SenderSnapshot.replyToAddress es más largo que el máximo declarado por su tipo (254)");
        }
    }
}
