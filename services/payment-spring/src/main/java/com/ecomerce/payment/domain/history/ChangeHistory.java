package com.ecomerce.payment.domain.history;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;

/**
 * El almacén del historial de cambios (DSL 2.29). Lo escriben los adaptadores de las raíces con historial, en la MISMA
 * transacción que la escritura (D1): una escritura que revierte no deja entrada. Ningún handler lo usa: la lectura entra
 * por el {@code historyOf} del puerto de la raíz, que antes la carga como su ficha (D7).
 */
public interface ChangeHistory {

    String CREATED = "created";
    String UPDATED = "updated";
    String DELETED = "deleted";
    String RESTORED = "restored";
    String ANONYMIZED = "anonymized";

    /** Escribe una entrada con el autor de la escritura en curso. */
    void record(String rootType, String rootId, String change, List<FieldChange> changes);

    /** Una página del historial de una raíz: la más reciente primero y, a igual instante, la de id mayor. */
    Page<HistoryEntry> page(String rootType, String rootId, HistoryFilter filter, Pageable pageable);

    /** Reescribe los valores personales de todas las entradas de la raíz y deja la entrada {@code anonymized} (D8). */
    void anonymize(String rootType, String rootId, List<PersonalPath> personal);

    /** Borra el historial de la raíz: su retención con {@code then: delete} o la purga de lo borrado (D8). */
    void purge(String rootType, String rootId);

    /** El valor de un dato del dominio como lo escribe el cable (el ObjectMapper de la aplicación). */
    JsonNode value(Object value);
}
