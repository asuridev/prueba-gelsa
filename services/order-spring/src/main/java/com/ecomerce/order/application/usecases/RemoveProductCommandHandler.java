package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.RemoveProductCommand;
import com.ecomerce.order.application.interfaces.CommandHandler;
import com.ecomerce.order.application.projection.ProductProjector;
import com.ecomerce.order.domain.aggregate.Product;
import com.ecomerce.order.domain.repository.ProductRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marca un producto como retirado en la copia local (lápida) cuando catalog lo retira.
 */
@ApplicationComponent
public class RemoveProductCommandHandler implements CommandHandler<RemoveProductCommand> {

    private static final Logger log = LoggerFactory.getLogger(RemoveProductCommandHandler.class);

    private final ProductRepository productRepository;
    private final ProductProjector productProjector;

    public RemoveProductCommandHandler(ProductRepository productRepository, ProductProjector productProjector) {
        this.productRepository = productRepository;
        this.productProjector = productProjector;
    }

    @Override
    @LogExceptions
    public void handle(RemoveProductCommand command) {
        Optional<Product> existing = productRepository.findById(command.id());
        if (existing.isPresent() && command.sourceVersion() < existing.get().getSourceVersion()) {
            log.atDebug().addKeyValue("keel.product_id", command.id())
                    .log("Retirada de producto descartada: su versión es menor que la guardada");
            return;
        }
        // Con copia: retired true con esa versión y sus demás datos intactos. Sin copia (la retirada
        // llegó antes que el alta): lápida con el id y la versión, sin datos de producto. La fila nunca se borra.
        productProjector.apply(command.id(), null, null, null, null, true, command.sourceVersion());
    }
}
