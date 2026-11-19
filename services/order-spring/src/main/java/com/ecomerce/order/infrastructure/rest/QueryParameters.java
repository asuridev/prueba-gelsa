package com.ecomerce.order.infrastructure.rest;

import com.ecomerce.order.infrastructure.web.JsonValueEnumConverterFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * La query de un listado (DSL 2.32): el orden que pide el cliente (?sort=) y las listas. Lee los valores CRUDOS de la
 * petición, porque el binding de Spring parte por comas un valor único y no varios, y el resolver del Pageable acepta
 * cualquier propiedad con cualquier dirección. Lo que se acepta lo fija keel-core (readClientSort y readQueryList): no
 * se ajusta a mano, porque los cuatro servidores del mismo diseño tienen que responder lo mismo.
 */
public final class QueryParameters {

    private static final JsonValueEnumConverterFactory ENUMS = new JsonValueEnumConverterFactory();

    private QueryParameters() {
    }

    /** El Pageable de la petición con el orden que pidió el cliente, o el del diseño si no pidió ninguno. */
    public static Pageable sorted(Pageable pageable, HttpServletRequest request, Map<String, String> sortable, Sort defaultOrder) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort(request.getParameterValues("sort"), sortable, defaultOrder));
    }

    /**
     * Lee ?sort=: cada valor es campo o campo,asc|desc, con el campo en sortable y la dirección en minúsculas; uno vacío,
     * con más de una coma o con el mismo campo dos veces es 400. El orden del cliente SUSTITUYE al del diseño.
     */
    public static Sort sort(String[] values, Map<String, String> sortable, Sort defaultOrder) {
        if (values == null || values.length == 0) {
            return defaultOrder;
        }
        List<Sort.Order> orders = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String[] parts = value.split(",", -1);
            String path = parts[0];
            String direction = parts.length > 1 ? parts[1] : "asc";
            String property = sortable.get(path);
            if (parts.length > 2 || property == null || !(direction.equals("asc") || direction.equals("desc")) || !seen.add(path)) {
                throw new InvalidQuery("Validation Error", "No se puede ordenar por '" + value + "'", null);
            }
            orders.add(direction.equals("desc") ? Sort.Order.desc(property) : Sort.Order.asc(property));
        }
        return Sort.by(orders);
    }

    /**
     * Lee una lista de la query: el parámetro repetido, y cada valor partido por comas (un elemento con coma solo viaja
     * repitiendo el parámetro). Ausente es null (no filtra); vacía, o con un elemento vacío, es 400.
     */
    public static <T> List<T> list(HttpServletRequest request, String name, Function<String, T> element) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length == 0) {
            return null;
        }
        List<T> read = new ArrayList<>();
        for (String value : values) {
            for (String part : value.split(",", -1)) {
                if (part.isEmpty()) {
                    throw new InvalidQuery("Validation Error", "La petición viola restricciones declaradas", name);
                }
                T converted;
                try {
                    converted = element.apply(part);
                } catch (RuntimeException unreadable) {
                    throw new InvalidQuery("Bad Request", "Petición malformada", null);
                }
                read.add(converted);
            }
        }
        return List.copyOf(read);
    }

    /** El lector de un elemento enum: el literal del contrato, con el mismo conversor que un @RequestParam. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends Enum<T>> Function<String, T> enumValue(Class<T> type) {
        Converter<String, T> converter = (Converter<String, T>) ENUMS.getConverter((Class) type);
        return converter::convert;
    }

    /** Una query que no se puede leer: 400 VALIDATION_ERROR (ApiExceptionHandler). {@code parameter} es la lista vacía. */
    public static final class InvalidQuery extends RuntimeException {

        private final String error;
        private final String parameter;

        InvalidQuery(String error, String message, String parameter) {
            super(message);
            this.error = error;
            this.parameter = parameter;
        }

        public String error() {
            return error;
        }

        public String parameter() {
            return parameter;
        }
    }
}
