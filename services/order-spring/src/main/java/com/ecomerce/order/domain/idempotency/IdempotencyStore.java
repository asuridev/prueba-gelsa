package com.ecomerce.order.domain.idempotency;

import java.util.Optional;

/**
 * Registro de peticiones ya atendidas, por clave de idempotencia.
 *
 * No confundir con IdempotencyGuard (infrastructure/messaging/idempotency): aquel
 * deduplica MENSAJES reentregados por el broker; este deduplica PETICIONES HTTP
 * repetidas por el cliente, identificadas por la cabecera Idempotency-Key.
 *
 * El contrato que impone el diseño no es rechazar la repetición, sino
 * reproducirla: la segunda llamada con la misma clave y el mismo contenido
 * devuelve la respuesta de la primera, sin volver a ejecutar nada. Por eso se
 * guarda el id del recurso resultante (para reconstruir la respuesta) y una
 * firma del contenido (para detectar la reutilización de la clave con otro
 * cuerpo, que el diseño resuelve con su propio error).
 */
public interface IdempotencyStore {

    /**
     * @param scope clave de agrupación: {@code command.idempotencyScope()}, que build compone
     *              desde el diseño — la operación, para que la misma cabecera en dos operaciones
     *              no colisione, y los campos de {@code idempotency.partitionBy}, para que dos
     *              llamantes con la misma clave no se vean
     * @param idempotencyKey la clave: el valor de la cabecera Idempotency-Key con
     *                       {@code keySource: client-key}, o CommandSignature.of(command)
     *                       con {@code payload-hash} — ahí no hay cabecera
     * @return el registro previo, si esa clave ya se usó y no ha expirado
     */
    Optional<StoredRequest> find(String scope, String idempotencyKey);

    /**
     * Registra la primera ejecución. Participa en la transacción del caso de uso:
     * si el comando revierte, el registro revierte con él y un reintento posterior
     * vuelve a ejecutarse con normalidad.
     *
     * @param signature  representación determinista del contenido de la petición
     * @param resourceId id del recurso resultante, con el que el handler reconstruye
     *                   la respuesta en una repetición (null si la operación no crea nada)
     * @param ttlSeconds ventana de deduplicación declarada por el diseño
     */
    void save(String scope, String idempotencyKey, String signature, String resourceId, long ttlSeconds);

    /** Lo guardado de la primera ejecución. */
    record StoredRequest(String signature, String resourceId) {
    }
}
