package com.ecomerce.order.infrastructure.configurations.transaction;

import com.ecomerce.order.application.support.AfterCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Adaptador de {@link AfterCommit}: engancha la acción al commit de la transacción que abrió el
 * UseCaseMediator. Si esa transacción revierte, la acción no se ejecuta.
 */
@Component
public class TransactionAfterCommit implements AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(TransactionAfterCommit.class);

    @Override
    public void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            execute(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                execute(action);
            }
        });
    }

    /**
     * El commit ya ocurrió: un fallo aquí no puede deshacerlo ni debe convertir en error una
     * operación que se confirmó. Se registra, con su pila, y se sigue.
     */
    private static void execute(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            log.warn("Efecto posterior a la confirmación fallido; la operación ya estaba confirmada", failure);
        }
    }
}
