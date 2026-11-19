package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import com.ecomerce.order.application.support.RawJsonDeserializer;
import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Registra que el cobro del pedido necesita que el comprador complete el 3DS.
 */
public record ApplyPaymentActionRequiredCommand(
        @NotBlank String chargeRequestId,
        @NotNull UUID paymentId,
        @JsonRawValue @JsonDeserialize(using = RawJsonDeserializer.class) @NotBlank String customerAction
) implements Command {
}
