package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.dtos.ListPaymentMethodsResponseDto;
import com.ecomerce.payment.application.interfaces.QueryHandler;
import com.ecomerce.payment.application.mappers.PaymentMethodApplicationMapper;
import com.ecomerce.payment.application.queries.ListPaymentMethodsQuery;
import com.ecomerce.payment.domain.aggregate.Wallet;
import com.ecomerce.payment.domain.entity.PaymentMethod;
import com.ecomerce.payment.domain.repository.WalletRepository;
import java.util.Comparator;
import java.util.List;

/**
 * Lista los medios active de un titular, primero el que está por defecto.
 */
@ApplicationComponent
public class ListPaymentMethodsQueryHandler implements QueryHandler<ListPaymentMethodsQuery, List<ListPaymentMethodsResponseDto>> {

    private final WalletRepository walletRepository;
    private final PaymentMethodApplicationMapper paymentMethodApplicationMapper;

    public ListPaymentMethodsQueryHandler(WalletRepository walletRepository, PaymentMethodApplicationMapper paymentMethodApplicationMapper) {
        this.walletRepository = walletRepository;
        this.paymentMethodApplicationMapper = paymentMethodApplicationMapper;
    }

    @Override
    @LogExceptions
    public List<ListPaymentMethodsResponseDto> handle(ListPaymentMethodsQuery query) {
        // Un titular sin Wallet devuelve una lista vacía, no un error. Como mucho son 10 medios: no se pagina.
        Wallet wallet = walletRepository.findByCustomerRef(query.customerRef()).orElse(null);
        if (wallet == null) {
            return List.of();
        }
        // El preferente primero; a igualdad de isDefault, por id (forma canónica en minúsculas, como texto): orden total.
        Comparator<PaymentMethod> order = Comparator
                .comparing((PaymentMethod method) -> !Boolean.TRUE.equals(method.getIsDefault()))
                .thenComparing(method -> method.getId().toString());
        return wallet.getPaymentMethods().stream()
                .filter(PaymentMethod::isActive)
                .sorted(order)
                .map(method -> paymentMethodApplicationMapper.toListPaymentMethodsResponseDto(method, wallet.getId()))
                .toList();
    }
}
