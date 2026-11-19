package com.ecomerce.order.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonValue;
import java.lang.reflect.Method;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * Convierte el literal del contrato (@JsonValue) al enum de dominio en los
 * parámetros de query y de ruta, igual que hace Jackson con el cuerpo JSON.
 */
public class JsonValueEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return new JsonValueEnumConverter<>(targetType);
    }

    private static final class JsonValueEnumConverter<T extends Enum> implements Converter<String, T> {

        private final Class<T> enumType;
        private final Method jsonValueAccessor;

        private JsonValueEnumConverter(Class<T> enumType) {
            this.enumType = enumType;
            this.jsonValueAccessor = findJsonValueAccessor(enumType);
        }

        @Override
        public T convert(String source) {
            String value = source.trim();
            if (value.isEmpty()) {
                return null;
            }
            for (T constant : enumType.getEnumConstants()) {
                if (value.equals(literalOf(constant)) || value.equalsIgnoreCase(constant.name())) {
                    return constant;
                }
            }
            throw new IllegalArgumentException(
                    "Valor no admitido para " + enumType.getSimpleName() + ": " + source);
        }

        private String literalOf(T constant) {
            if (jsonValueAccessor == null) {
                return constant.name();
            }
            try {
                Object literal = jsonValueAccessor.invoke(constant);
                return literal != null ? literal.toString() : null;
            } catch (ReflectiveOperationException exception) {
                return constant.name();
            }
        }

        private static Method findJsonValueAccessor(Class<?> enumType) {
            for (Method method : enumType.getMethods()) {
                if (method.getParameterCount() == 0 && method.isAnnotationPresent(JsonValue.class)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            return null;
        }
    }
}
