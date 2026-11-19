package com.ecomerce.payment.application.interfaces;

public interface CommandHandler<T extends Command> extends Handler {

    void handle(T command);
}
