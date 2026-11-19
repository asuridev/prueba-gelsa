package com.ecomerce.payment.application.queries;

import com.ecomerce.payment.application.dtos.GetPaymentByChargeRequestResponseDto;
import com.ecomerce.payment.application.interfaces.Query;

/**
 * Recupera un cobro por la clave de negocio con la que lo pidió el consumidor.
 */
public record GetPaymentByChargeRequestQuery(
        String chargeRequestId
) implements Query<GetPaymentByChargeRequestResponseDto> {
}
