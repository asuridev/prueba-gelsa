package com.ecomerce.payment.infrastructure.configurations.usecase;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.domain.annotations.DomainComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(
        basePackages = { "com.ecomerce.payment" },
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = ApplicationComponent.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainComponent.class)
        })
public class UseCaseConfig {
}
