package com.ecomerce.notifications.infrastructure.serialization;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.MapperFeature;

/**
 * Instala {@link TimestampModule} en el JsonMapper de la aplicación.
 *
 * Ese mapper es el que usan las respuestas REST y —por autoconfiguración— el
 * MessageConverter del broker, así que el formato de los instantes es el mismo
 * en el cuerpo de una respuesta y en el payload de un evento de integración.
 *
 * Los defaults de Jackson 2 los pone {@code spring.jackson.use-jackson2-defaults}
 * (application.yaml) salvo uno: en Boot 3 los nombres de los parámetros de un
 * constructor los leía el ParameterNamesModule que registraba Boot, y en Jackson 3
 * ese módulo es {@code MapperFeature.DETECT_PARAMETER_NAMES}, que
 * {@code configureForJackson2()} apaga. Se vuelve a encender aquí para que un
 * constructor sin {@code @JsonCreator} se siga pudiendo usar al leer.
 *
 * Deliberadamente NO se toca la inclusión de propiedades nulas: "ausencia vs.
 * nulo" es una convención de determinación del diseño y se implementa con
 * @JsonInclude en las clases que la necesiten, no como default global (ver
 * docs/keel/conventions/mapping.md).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer timestampPrecisionCustomizer() {
        return builder -> builder
                .addModule(new TimestampModule())
                .enable(MapperFeature.DETECT_PARAMETER_NAMES);
    }
}
