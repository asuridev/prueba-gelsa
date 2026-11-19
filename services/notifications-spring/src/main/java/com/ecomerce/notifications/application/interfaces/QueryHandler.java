package com.ecomerce.notifications.application.interfaces;

public interface QueryHandler<Q extends Query<R>, R> extends Handler {

    R handle(Q query);
}
