package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.dtos.GetPaymentByChargeRequestResponseDto;
import com.ecomerce.payment.application.interfaces.QueryHandler;
import com.ecomerce.payment.application.mappers.PaymentApplicationMapper;
import com.ecomerce.payment.application.queries.GetPaymentByChargeRequestQuery;
import com.ecomerce.payment.domain.errors.PaymentNotFoundError;
import com.ecomerce.payment.domain.repository.PaymentRepository;

/**
 * Recupera un cobro por la clave de negocio con la que lo pidió el consumidor.
 */
@ApplicationComponent
public class GetPaymentByChargeRequestQueryHandler implements QueryHandler<GetPaymentByChargeRequestQuery, GetPaymentByChargeRequestResponseDto> {

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;

    public GetPaymentByChargeRequestQueryHandler(PaymentRepository paymentRepository, PaymentApplicationMapper paymentApplicationMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentApplicationMapper = paymentApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetPaymentByChargeRequestResponseDto handle(GetPaymentByChargeRequestQuery query) {
        // Las devoluciones viajan dentro del cobro, de la más antigua a la más reciente (lo ordena Payment.getRefunds()).
        return paymentRepository.findByChargeRequestId(query.chargeRequestId())
                .map(paymentApplicationMapper::toGetPaymentByChargeRequestResponseDto)
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese chargeRequestId"));
    }
}
