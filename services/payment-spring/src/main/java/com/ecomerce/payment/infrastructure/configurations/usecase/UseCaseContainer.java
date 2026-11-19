package com.ecomerce.payment.infrastructure.configurations.usecase;

import com.ecomerce.payment.application.interfaces.Dispatchable;
import com.ecomerce.payment.application.interfaces.Handler;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registro mensaje → handler que alimenta al UseCaseMediator.
 */
@Component
public class UseCaseContainer {

    private final Map<Class<? extends Dispatchable>, Handler> instances = new HashMap<>();

    public void register(Class<? extends Dispatchable> type, Handler useCase) {
        instances.put(type, useCase);
    }

    public Handler resolve(Class<? extends Dispatchable> type) {
        Handler instance = instances.get(type);
        if (instance == null) {
            throw new IllegalArgumentException("No hay handler registrado para el tipo: " + type.getName());
        }
        return instance;
    }
}
