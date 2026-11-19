package com.ecomerce.payment.application.dtos;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Respuesta paginada del contrato: items + metadatos de página.
 */
public record PagedResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    /**
     * Sin elementos es la página vacía del contrato: cero elementos y cero páginas, también fuera de rango, con la
     * página y el tamaño que se pidieron. La Page de Spring Data conserva ahí los totales reales.
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        if (page.getContent().isEmpty()) {
            return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), 0, 0);
        }
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
