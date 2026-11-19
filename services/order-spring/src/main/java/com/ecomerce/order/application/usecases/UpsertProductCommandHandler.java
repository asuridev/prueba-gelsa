package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.UpsertProductCommand;
import com.ecomerce.order.application.interfaces.CommandHandler;
import com.ecomerce.order.application.projection.ProductProjector;
import com.ecomerce.order.domain.aggregate.Product;
import com.ecomerce.order.domain.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Da de alta o actualiza un producto en la copia local del catálogo.
 */
@ApplicationComponent
public class UpsertProductCommandHandler implements CommandHandler<UpsertProductCommand> {

    private static final Logger log = LoggerFactory.getLogger(UpsertProductCommandHandler.class);

    private final ProductRepository productRepository;
    private final ProductProjector productProjector;

    public UpsertProductCommandHandler(ProductRepository productRepository, ProductProjector productProjector) {
        this.productRepository = productRepository;
        this.productProjector = productProjector;
    }

    @Override
    @LogExceptions
    public void handle(UpsertProductCommand command) {
        // Un precio que catalog ya no sostiene (<= 0) no se rechaza: el producto pasa a lápida.
        boolean unsustainedPrice = command.price().compareTo(BigDecimal.ZERO) <= 0;
        Optional<Product> existing = productRepository.findById(command.id());
        if (existing.isEmpty()) {
            if (unsustainedPrice) {
                productProjector.apply(command.id(), null, null, null, null, true, command.sourceVersion());
            } else {
                productProjector.apply(command.id(), command.sku(), command.name(), command.price(), command.currency(), false, command.sourceVersion());
            }
            return;
        }
        Product current = existing.get();
        if (command.sourceVersion() <= current.getSourceVersion()) {
            log.atDebug().addKeyValue("keel.product_id", command.id())
                    .log("Evento de producto descartado: su versión no supera la guardada");
            return;
        }
        if (unsustainedPrice) {
            // Conserva los demás datos: solo la marca de retirado y la versión.
            productProjector.apply(command.id(), null, null, null, null, true, command.sourceVersion());
            return;
        }
        // Sobrescribe, también una lápida, que vuelve a estar a la venta.
        productProjector.apply(command.id(), command.sku(), command.name(), command.price(), command.currency(), false, command.sourceVersion());
    }
}
