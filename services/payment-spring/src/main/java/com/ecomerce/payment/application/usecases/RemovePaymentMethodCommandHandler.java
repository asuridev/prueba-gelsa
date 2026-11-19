package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.RemovePaymentMethodCommand;
import com.ecomerce.payment.application.interfaces.CommandHandler;
import com.ecomerce.payment.domain.aggregate.Wallet;
import com.ecomerce.payment.domain.errors.PaymentMethodNotFoundError;
import com.ecomerce.payment.domain.repository.WalletRepository;

/**
 * Retira un medio guardado del titular; deja de poder cobrarse.
 */
@ApplicationComponent
public class RemovePaymentMethodCommandHandler implements CommandHandler<RemovePaymentMethodCommand> {

    private final WalletRepository walletRepository;

    public RemovePaymentMethodCommandHandler(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Override
    @LogExceptions
    public void handle(RemovePaymentMethodCommand command) {
        // No se distingue si el medio no existe o es de otro titular: 404 en los dos casos.
        Wallet wallet = walletRepository.findByCustomerRef(command.customerRef())
                .orElseThrow(() -> new PaymentMethodNotFoundError("El medio no existe o no es del titular"));
        // Retirar un medio que ya estaba removed no hace nada y no es un error. Los cobros ya hechos conservan su paymentMethodId.
        if (wallet.removePaymentMethod(command.paymentMethodId())) {
            walletRepository.save(wallet);
        }
    }
}
