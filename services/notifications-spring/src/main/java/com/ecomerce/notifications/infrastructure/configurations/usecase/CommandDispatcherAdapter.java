package com.ecomerce.notifications.infrastructure.configurations.usecase;

import com.ecomerce.notifications.application.interfaces.Command;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import com.ecomerce.notifications.application.port.out.CommandDispatcher;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link CommandDispatcher} sobre el {@link UseCaseMediator}. Vive
 * en infraestructura porque es aquí donde se conoce el mediator; la capa application
 * solo ve el puerto.
 */
@Component
public class CommandDispatcherAdapter implements CommandDispatcher {

    private final UseCaseMediator mediator;

    public CommandDispatcherAdapter(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    @Override
    public void dispatch(Command command) {
        mediator.dispatch(command);
    }

    @Override
    public <R> R dispatch(ReturningCommand<R> command) {
        return mediator.dispatch(command);
    }

    @Override
    public void dispatchWithoutTransaction(Command command) {
        mediator.dispatchWithoutTransaction(command);
    }

    @Override
    public <R> R dispatchWithoutTransaction(ReturningCommand<R> command) {
        return mediator.dispatchWithoutTransaction(command);
    }
}
