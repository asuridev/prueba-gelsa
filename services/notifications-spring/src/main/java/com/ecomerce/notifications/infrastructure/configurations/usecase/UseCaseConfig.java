package com.ecomerce.notifications.infrastructure.configurations.usecase;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.domain.annotations.DomainComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(
        basePackages = { "com.ecomerce.notifications" },
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = ApplicationComponent.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainComponent.class)
        })
public class UseCaseConfig {
}
