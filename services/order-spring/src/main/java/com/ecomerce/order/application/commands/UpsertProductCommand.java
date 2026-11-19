package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Da de alta o actualiza un producto en la copia local del catálogo.
 */
public record UpsertProductCommand(
        @NotNull UUID id,
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal price,
        // El @Pattern del value type CurrencyCode (^[A-Z]{3}$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con CurrencyCodeFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull Long sourceVersion
) implements Command {

    // constraints.scale — el importe se pone a su escala al entrar (con round se redondea; con reject solo se rellena).
    public UpsertProductCommand {
        if (price != null) {
            price = price.setScale(3, RoundingMode.HALF_UP);
        }
    }
}
