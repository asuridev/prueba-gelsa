package com.ecomerce.payment.application.queries;

import com.ecomerce.payment.application.dtos.ListPaymentsByCustomerResponseDto;
import com.ecomerce.payment.application.dtos.PagedResponse;
import com.ecomerce.payment.application.interfaces.Query;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;

/**
 * Lista los cobros de un titular, del más reciente al más antiguo.
 */
public record ListPaymentsByCustomerQuery(
        @NotBlank @Size(min = 1, max = 200) String customerRef,
        Pageable pageable
) implements Query<PagedResponse<ListPaymentsByCustomerResponseDto>> {
}
