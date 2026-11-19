package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Marca un producto como retirado en la copia local (lápida) cuando catalog lo retira.
 */
public record RemoveProductCommand(
        @NotNull UUID id,
        @NotNull Long sourceVersion
) implements Command {
}
