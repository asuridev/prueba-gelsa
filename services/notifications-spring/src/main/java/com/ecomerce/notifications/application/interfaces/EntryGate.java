package com.ecomerce.notifications.application.interfaces;

/**
 * La puerta por la que entra una operación. Solo la lleva el mensaje de una operación que entra por HTTP y por alguna
 * otra: HTTP es la única que puede responder un error a quien llamó, y las reglas del diseño distinguen por ella.
 */
public enum EntryGate {
    HTTP,
    SUBSCRIPTION,
    DISPATCH,
    SCHEDULE
}
