package com.ecomerce.payment.domain.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

/**
 * Un campo que cambió en una escritura (wire-contract.md § Historial): su ruta del contrato y sus valores antes y después,
 * con las reglas del cable. {@code null} cuando no había o ya no hay, y se escribe aunque la convención de nulos del
 * servicio los omita: un historial que calla el {@code before} no dice desde qué valor cambió.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FieldChange(String field, JsonNode before, JsonNode after) {
}
