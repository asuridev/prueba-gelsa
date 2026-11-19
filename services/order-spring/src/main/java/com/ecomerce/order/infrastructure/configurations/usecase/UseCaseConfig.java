package com.ecomerce.order.infrastructure.configurations.usecase;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.domain.annotations.DomainComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(
        basePackages = { "com.ecomerce.order" },
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = ApplicationComponent.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainComponent.class)
        })
public class UseCaseConfig {
}
