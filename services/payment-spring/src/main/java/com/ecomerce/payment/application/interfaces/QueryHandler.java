package com.ecomerce.payment.application.interfaces;

public interface QueryHandler<Q extends Query<R>, R> extends Handler {

    R handle(Q query);
}
