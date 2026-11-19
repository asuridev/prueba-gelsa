package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.dtos.GetPaymentResponseDto;
import com.ecomerce.payment.application.interfaces.QueryHandler;
import com.ecomerce.payment.application.mappers.PaymentApplicationMapper;
import com.ecomerce.payment.application.queries.GetPaymentQuery;
import com.ecomerce.payment.domain.errors.PaymentNotFoundError;
import com.ecomerce.payment.domain.repository.PaymentRepository;

/**
 * Recupera un cobro por su id, con sus devoluciones.
 */
@ApplicationComponent
public class GetPaymentQueryHandler implements QueryHandler<GetPaymentQuery, GetPaymentResponseDto> {

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;

    public GetPaymentQueryHandler(PaymentRepository paymentRepository, PaymentApplicationMapper paymentApplicationMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentApplicationMapper = paymentApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetPaymentResponseDto handle(GetPaymentQuery query) {
        // Las devoluciones viajan dentro del cobro, de la más antigua a la más reciente (lo ordena Payment.getRefunds()).
        return paymentRepository.findById(query.id())
                .map(paymentApplicationMapper::toGetPaymentResponseDto)
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese id"));
    }
}
