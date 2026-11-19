package com.ecomerce.order.domain.identity;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Identificadores UUID versión 7 (RFC 9562): 48 bits de milisegundos delante, versión, y 74 bits
 * aleatorios.
 *
 * <p>Es la forma de crear el id de una raíz nueva, en vez de {@code UUID.randomUUID()} (versión 4,
 * aleatoria). Con la 4, cada alta cae en un punto cualquiera del índice de la PK, y en los motores
 * que guardan la tabla ordenada por ella (MySQL, MariaDB) eso reordena la tabla con cada inserción.
 * Con la 7 cada id nuevo va al final. SQL Server ordena el uniqueidentifier por sus últimos bytes:
 * allí no gana nada, ni pierde. El contrato no cambia: sigue siendo un UUID.
 *
 * <p>Dentro del mismo milisegundo el orden lo decide la parte aleatoria, así que dos ids del mismo
 * milisegundo no salen necesariamente en orden de creación: lo que se gana es localidad, no un
 * contador. Y el id deja ver el instante en que se creó; si eso fuera un dato sensible para algún
 * recurso, su id no debe salir de aquí.
 */
public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    /** Un UUID versión 7 nuevo. */
    public static UUID v7() {
        long millis = System.currentTimeMillis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        // 48 bits de tiempo | versión 7 | 12 bits aleatorios.
        long msb = (millis << 16) | 0x7000L | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
        // Variante 10 | 62 bits aleatorios.
        long lsb = 0x8000000000000000L | ((random[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            lsb |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
