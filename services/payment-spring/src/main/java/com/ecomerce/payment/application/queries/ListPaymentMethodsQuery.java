package com.ecomerce.payment.application.queries;

import com.ecomerce.payment.application.dtos.ListPaymentMethodsResponseDto;
import com.ecomerce.payment.application.interfaces.Query;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lista los medios active de un titular, primero el que está por defecto.
 */
public record ListPaymentMethodsQuery(
        @NotBlank @Size(min = 1, max = 200) String customerRef
) implements Query<List<ListPaymentMethodsResponseDto>> {
}
