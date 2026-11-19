package com.ecomerce.notifications.infrastructure.configurations.usecase;

import com.ecomerce.notifications.application.interfaces.CommandHandler;
import com.ecomerce.notifications.application.interfaces.Dispatchable;
import com.ecomerce.notifications.application.interfaces.Handler;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, descubre los handlers del contexto Spring y los registra en el
 * UseCaseContainer, deduciendo por reflexión el tipo de mensaje que maneja
 * cada uno.
 */
@Component
public class UseCaseAutoRegister implements CommandLineRunner {

    private final UseCaseContainer useCaseContainer;
    private final ApplicationContext applicationContext;

    public UseCaseAutoRegister(UseCaseContainer useCaseContainer, ApplicationContext applicationContext) {
        this.useCaseContainer = useCaseContainer;
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void run(String... args) {
        Map<String, CommandHandler> commandHandlers = applicationContext.getBeansOfType(CommandHandler.class);
        commandHandlers.values().forEach(handler -> useCaseContainer.register(getGenericType(handler.getClass()), handler));

        Map<String, QueryHandler> queryHandlers = applicationContext.getBeansOfType(QueryHandler.class);
        queryHandlers.values().forEach(handler -> useCaseContainer.register(getGenericType(handler.getClass()), handler));

        Map<String, ReturningCommandHandler> returningCommandHandlers = applicationContext.getBeansOfType(ReturningCommandHandler.class);
        returningCommandHandlers.values().forEach(handler -> useCaseContainer.register(getGenericType(handler.getClass()), handler));
    }

    @SuppressWarnings("unchecked")
    private Class<Dispatchable> getGenericType(Class<?> handlerClass) {
        Class<?> currentClass = handlerClass;
        while (currentClass != null) {
            for (Type genericInterface : currentClass.getGenericInterfaces()) {
                if (genericInterface instanceof ParameterizedType parameterizedType
                        && parameterizedType.getRawType() instanceof Class<?> interfaceClass
                        && Handler.class.isAssignableFrom(interfaceClass)) {
                    return (Class<Dispatchable>) parameterizedType.getActualTypeArguments()[0];
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new IllegalArgumentException("No se puede deducir el tipo de mensaje del handler: " + handlerClass.getName());
    }
}
