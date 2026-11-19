package com.ecomerce.payment.infrastructure.serialization;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Serializa todo Instant en ISO-8601 UTC con exactamente tres dígitos de
 * fracción de segundo ("2026-07-26T09:21:07.482Z").
 *
 * El formato temporal es contrato: lo fijan las Convenciones de determinación
 * de specs/validation-scenarios.md, y un escenario que compara la forma de un
 * createdAt no puede depender de si el Instant nació de Instant.now() o de una
 * columna TIMESTAMP. appendInstant(3) rellena o trunca hasta los milisegundos.
 *
 * Si el diseño declara otra precisión, se cambia el 3 aquí y en ningún otro
 * sitio: este módulo es el único punto donde el servicio decide el formato.
 */
public class TimestampModule extends SimpleModule {

    private static final DateTimeFormatter ISO_MILLIS = new DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();

    public TimestampModule() {
        addSerializer(Instant.class, new ValueSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializationContext context) {
                gen.writeString(ISO_MILLIS.format(value));
            }
        });
    }
}
