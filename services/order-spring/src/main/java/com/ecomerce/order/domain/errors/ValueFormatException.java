package com.ecomerce.order.domain.errors;

/**
 * Un valor no cumple el formato de su value type declarado. Lo lanza la clase
 * <Tipo>Format del dominio, que es donde vive la regex del diseño.
 *
 * Sobre una petición HTTP el formato ya lo rechaza el DTO de entrada; esto es la
 * guarda del dominio, para lo que llega por otra puerta (un evento, una operación
 * interna). Mismo code y status que la validación de entrada
 * (conventions/mapping.md § El formato se valida en la entrada).
 */
public class ValueFormatException extends BadRequestException {

    public ValueFormatException(String message) {
        super(message, "VALIDATION_ERROR", 400, null);
    }
}
