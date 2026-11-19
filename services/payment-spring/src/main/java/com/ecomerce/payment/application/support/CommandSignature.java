package com.ecomerce.payment.application.support;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Firma determinista del contenido de un Command.
 *
 * Dos usos, según el {@code keySource} que declare la operación:
 * <ul>
 *   <li>{@code client-key}: es la FIRMA que se guarda junto a la clave del cliente.
 *       Sirve para detectar que alguien reutilizó su Idempotency-Key con otro cuerpo.</li>
 *   <li>{@code payload-hash}: es la CLAVE misma. No hay cabecera que leer — el diseño
 *       dice que el mismo contenido significa la misma intención.</li>
 * </ul>
 *
 * <p>Por qué está generada y no la escribe cada handler: la firma se compara contra
 * una guardada en otro despliegue. Si dos handlers la calculan distinto —o el mismo
 * la calcula distinto tras un refactor— la comparación deja de significar nada y no
 * hay nada que lo delate: el sistema simplemente deja de deduplicar.
 *
 * <p>Canónica quiere decir: componentes de record ordenados por nombre, cada escalar
 * precedido de su longitud (así ningún contenido puede imitar un separador) y el
 * orden de una lista conservado, porque es parte del contenido. Un null se codifica
 * como marca propia en vez de omitirse: "ausencia vs. nulo" es una convención de
 * determinación del diseño, y colapsarlos daría la misma firma a dos peticiones que
 * el contrato distingue. Un binario entra por su digest, nunca por su identidad de
 * objeto.
 *
 * <p>No usa Jackson a propósito: el ObjectMapper de la aplicación lo configuran la
 * serialización de la API y el broker, y un cambio ahí —una precisión temporal, un
 * @JsonInclude— movería en silencio firmas ya almacenadas.
 */
public final class CommandSignature {

    private CommandSignature() {
        // Clase de utilidad.
    }

    /** @return SHA-256 en hexadecimal de la forma canónica del command */
    public static String of(Object command) {
        return HexFormat.of().formatHex(sha256(canonical(command).getBytes(StandardCharsets.UTF_8)));
    }

    /** Forma canónica: visible para poder verificarla en una prueba. */
    static String canonical(Object value) {
        if (value == null) {
            return "~";
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(CommandSignature::canonical).orElse("~");
        }
        // La escala forma parte del valor en BigDecimal: 1.50 y 1.5 son el mismo
        // importe y deben dar la misma firma.
        if (value instanceof BigDecimal number) {
            return scalar(number.stripTrailingZeros().toPlainString());
        }
        // Un binario (el contenido de un FileUpload) puede pesar megas: lo que entra
        // en la firma es su digest. Lo que NUNCA puede entrar es lo que devolvería
        // String.valueOf de un array — la identidad del objeto, distinta en cada
        // ejecución: la operación dejaría de deduplicar y nada lo delataría.
        if (value instanceof byte[] bytes) {
            return scalar(HexFormat.of().formatHex(sha256(bytes)));
        }
        if (value.getClass().isArray()) {
            return IntStream.range(0, Array.getLength(value))
                    .mapToObj(index -> canonical(Array.get(value, index)))
                    .collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> scalar(String.valueOf(entry.getKey())) + canonical(entry.getValue()))
                    .sorted()
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Collection<?> items) {
            // El orden SÍ cuenta: dos listas con los mismos elementos en distinto
            // orden son dos peticiones distintas para cualquier lector del diseño.
            return items.stream().map(CommandSignature::canonical).collect(Collectors.joining(",", "[", "]"));
        }
        if (value.getClass().isRecord()) {
            return Arrays.stream(value.getClass().getRecordComponents())
                    .sorted(Comparator.comparing(RecordComponent::getName))
                    .map(component -> scalar(component.getName()) + canonical(read(component, value)))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        return scalar(String.valueOf(value));
    }

    private static Object read(RecordComponent component, Object owner) {
        try {
            return component.getAccessor().invoke(owner);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("No se pudo leer el componente " + component.getName(), failure);
        }
    }

    /** Prefijo de longitud: hace imposible que un contenido imite un separador. */
    private static String scalar(String raw) {
        return raw.length() + ":" + raw;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", unavailable);
        }
    }
}
