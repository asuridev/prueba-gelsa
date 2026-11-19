package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.dtos.CreateOrderResponseDto;
import com.ecomerce.order.application.interfaces.ReturningCommand;
import com.ecomerce.order.domain.valueobject.OrderLineRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Crea un pedido con las líneas pedidas, calcula su importe con la copia local del catálogo y pide su cobro a payment, respondiendo con el pedido en el estado que alcanzó.
 */
public record CreateOrderCommand(
        // La resuelve el servidor desde la credencial (security.authentication.callerIdentity):
        // no llega del cuerpo, la estampa el controller. Por el canal de eventos la estampa el listener.
        @JsonIgnore String customerRef,
        // El correo del comprador sale del claim email de su token (CallerIdentity.claim): no llega del cuerpo, lo estampa el controller.
        // Null o inválido lo rechaza el handler con CUSTOMER_EMAIL_REQUIRED.
        @JsonIgnore String customerEmail,
        @NotEmpty @Size(min = 1, max = 50) @Valid List<OrderLineRequest> lines,
        @Size(min = 1, max = 255) String paymentToken,
        UUID paymentMethodId
) implements ReturningCommand<CreateOrderResponseDto> {

    // Una lista no informada es una lista vacía, nunca null (contrato del cable).
    public CreateOrderCommand {
        lines = lines == null ? List.of() : lines;
    }

    /**
     * Ámbito de la clave de idempotencia: la operación y {@code customerRef} (idempotency.partitionBy): dos llamantes con la misma clave no se ven.
     * Es el primer argumento de IdempotencyStore.find/save; no lo compongas a mano.
     */
    public String idempotencyScope() {
        return "createOrder" + ":" + String.valueOf(customerRef);
    }
}
