package com.ecomerce.payment.application.support;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Lee un campo `json` del cable y lo guarda como el texto del documento.
 *
 * Es la otra mitad de {@code @JsonRawValue}: al escribir, el String se emite tal cual,
 * embebido; al leer, un objeto o un array llega aquí como árbol y se vuelve a texto. Una
 * cadena también se acepta, y se toma como el documento ya serializado, para no romper a
 * un emisor que todavía lo mande escapado.
 */
public class RawJsonDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isString() ? node.stringValue() : node.toString();
    }
}
