package com.ecomerce.payment.infrastructure.rest.controllers.paymentmethod.v1;

import com.ecomerce.payment.application.commands.RemovePaymentMethodCommand;
import com.ecomerce.payment.application.commands.SavePaymentMethodCommand;
import com.ecomerce.payment.application.commands.SetDefaultPaymentMethodCommand;
import com.ecomerce.payment.application.dtos.ListPaymentMethodsResponseDto;
import com.ecomerce.payment.application.dtos.SavePaymentMethodResponseDto;
import com.ecomerce.payment.application.dtos.SetDefaultPaymentMethodResponseDto;
import com.ecomerce.payment.application.queries.ListPaymentMethodsQuery;
import com.ecomerce.payment.infrastructure.configurations.usecase.UseCaseMediator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "PaymentMethod", description = "Cobra con tarjeta, a través de una pasarela intercambiable, los importes que le piden otros servidores — autorizar, capturar, anular y devolver —, y guarda medios de pago para cobrar sin el cliente presente.")
public class PaymentMethodV1Controller {

    private final UseCaseMediator mediator;

    public PaymentMethodV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Guarda en el Wallet del titular el medio de pago que representa un token de la pasarela, para cobrarle después sin que esté presente.
     */
    @Operation(summary = "Guarda en el Wallet del titular el medio de pago que representa un token de la pasarela, para cobrarle después sin que esté presente.")
    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public SavePaymentMethodResponseDto savePaymentMethod(@Valid @RequestBody SavePaymentMethodCommand command) {
        return mediator.dispatchWithoutTransaction(command);
    }

    /**
     * Lista los medios active de un titular, primero el que está por defecto.
     */
    @Operation(summary = "Lista los medios active de un titular, primero el que está por defecto.")
    @GetMapping("/payment-methods")
    public List<ListPaymentMethodsResponseDto> listPaymentMethods(@RequestParam @NotBlank @Size(min = 1, max = 200) String customerRef) {
        return mediator.dispatch(new ListPaymentMethodsQuery(customerRef));
    }

    /**
     * Marca un medio del titular como el preferente.
     */
    @Operation(summary = "Marca un medio del titular como el preferente.")
    @PostMapping("/payment-methods/{paymentMethodId}/default")
    public SetDefaultPaymentMethodResponseDto setDefaultPaymentMethod(@PathVariable UUID paymentMethodId, @Valid @RequestBody SetDefaultPaymentMethodCommand command) {
        return mediator.dispatch(new SetDefaultPaymentMethodCommand(paymentMethodId, command.customerRef()));
    }

    /**
     * Retira un medio guardado del titular; deja de poder cobrarse.
     */
    @Operation(summary = "Retira un medio guardado del titular; deja de poder cobrarse.")
    @DeleteMapping("/payment-methods/{paymentMethodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePaymentMethod(@PathVariable UUID paymentMethodId, @RequestParam @NotBlank @Size(min = 1, max = 200) String customerRef) {
        mediator.dispatch(new RemovePaymentMethodCommand(paymentMethodId, customerRef));
    }
}
