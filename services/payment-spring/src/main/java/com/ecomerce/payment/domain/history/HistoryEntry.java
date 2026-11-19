package com.ecomerce.payment.domain.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Una entrada del historial: una escritura confirmada sobre el agregado (D2). {@code change} es {@code created},
 * {@code updated}, {@code deleted}, {@code restored} o {@code anonymized}; {@code author} es el actor de la autoría, o el
 * centinela {@code system} fuera de una petición.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HistoryEntry(Instant at, String author, String change, List<FieldChange> changes) {
}
