package com.ecomerce.payment.application.queries;

import com.ecomerce.payment.application.dtos.GetPaymentResponseDto;
import com.ecomerce.payment.application.interfaces.Query;
import java.util.UUID;

/**
 * Recupera un cobro por su id, con sus devoluciones.
 */
public record GetPaymentQuery(
        UUID id
) implements Query<GetPaymentResponseDto> {
}
