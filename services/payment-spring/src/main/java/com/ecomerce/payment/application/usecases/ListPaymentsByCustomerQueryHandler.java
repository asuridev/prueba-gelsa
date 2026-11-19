package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.dtos.ListPaymentsByCustomerResponseDto;
import com.ecomerce.payment.application.dtos.PagedResponse;
import com.ecomerce.payment.application.interfaces.QueryHandler;
import com.ecomerce.payment.application.mappers.PaymentApplicationMapper;
import com.ecomerce.payment.application.queries.ListPaymentsByCustomerQuery;
import com.ecomerce.payment.domain.repository.PaymentRepository;

/**
 * Lista los cobros de un titular, del más reciente al más antiguo.
 */
@ApplicationComponent
public class ListPaymentsByCustomerQueryHandler implements QueryHandler<ListPaymentsByCustomerQuery, PagedResponse<ListPaymentsByCustomerResponseDto>> {

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;

    public ListPaymentsByCustomerQueryHandler(PaymentRepository paymentRepository, PaymentApplicationMapper paymentApplicationMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentApplicationMapper = paymentApplicationMapper;
    }

    @Override
    @LogExceptions
    public PagedResponse<ListPaymentsByCustomerResponseDto> handle(ListPaymentsByCustomerQuery query) {
        // Un titular sin cobros devuelve una página vacía, no un error.
        return PagedResponse.from(paymentRepository
                .listPaymentsByCustomer(new PaymentRepository.ListPaymentsByCustomerFilter(query.customerRef()), query.pageable())
                .map(paymentApplicationMapper::toListPaymentsByCustomerResponseDto));
    }
}
