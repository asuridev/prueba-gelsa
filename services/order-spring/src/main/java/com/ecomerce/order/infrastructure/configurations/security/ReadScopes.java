package com.ecomerce.order.infrastructure.configurations.security;

import com.ecomerce.order.domain.repository.ReadScope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * El {@link ReadScope} de cada lectura acotada (DSL 2.27), construido desde el token de la petición en curso: lo estampa
 * el controller en el mensaje y el handler no lo vuelve a mirar. Solo una exención da {@code unrestricted} (D3).
 */
@Component
public class ReadScopes {

    /**
     * security.ownership.Order: Order.customerRef = identidad del llamante.
     * Si no, el dueño es la identidad del llamante (callerIdentity), la misma que estampa el controller.
     */
    public ReadScope ownerOfOrder() {
        return ReadScope.ownedBy(CallerIdentity.resolve());
    }
}
