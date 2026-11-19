package com.ecomerce.order.infrastructure.configurations;

import com.ecomerce.order.infrastructure.web.JsonValueEnumConverterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de binding y serialización HTTP del servicio.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new JsonValueEnumConverterFactory());
    }
}
