package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.SetDefaultPaymentMethodCommand;
import com.ecomerce.payment.application.dtos.SetDefaultPaymentMethodResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommandHandler;
import com.ecomerce.payment.application.mappers.PaymentMethodApplicationMapper;
import com.ecomerce.payment.domain.aggregate.Wallet;
import com.ecomerce.payment.domain.entity.PaymentMethod;
import com.ecomerce.payment.domain.errors.PaymentMethodNotFoundError;
import com.ecomerce.payment.domain.repository.WalletRepository;

/**
 * Marca un medio del titular como el preferente.
 */
@ApplicationComponent
public class SetDefaultPaymentMethodCommandHandler implements ReturningCommandHandler<SetDefaultPaymentMethodCommand, SetDefaultPaymentMethodResponseDto> {

    private final WalletRepository walletRepository;
    private final PaymentMethodApplicationMapper paymentMethodApplicationMapper;

    public SetDefaultPaymentMethodCommandHandler(WalletRepository walletRepository, PaymentMethodApplicationMapper paymentMethodApplicationMapper) {
        this.walletRepository = walletRepository;
        this.paymentMethodApplicationMapper = paymentMethodApplicationMapper;
    }

    @Override
    @LogExceptions
    public SetDefaultPaymentMethodResponseDto handle(SetDefaultPaymentMethodCommand command) {
        // No se distingue si el medio no existe, está retirado o es de otro titular: 404 en los tres casos.
        Wallet wallet = walletRepository.findByCustomerRef(command.customerRef())
                .orElseThrow(() -> new PaymentMethodNotFoundError("El medio no existe, está retirado o no es del titular"));
        // El anterior preferente deja de serlo; si el medio ya era el preferente, no cambia nada.
        wallet.setDefault(command.paymentMethodId());
        Wallet saved = walletRepository.save(wallet);
        PaymentMethod method = saved.findActive(command.paymentMethodId())
                .orElseThrow(() -> new PaymentMethodNotFoundError("El medio no existe, está retirado o no es del titular"));
        return paymentMethodApplicationMapper.toSetDefaultPaymentMethodResponseDto(method, saved.getId());
    }
}
