package com.ecomerce.payment.infrastructure.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * La conversión entre el importe del diseño (decimal en unidades mayores) y el de la pasarela.
 * Nunca redondea: un importe con más decimales de los que admite la moneda es un error, no un
 * céntimo que se pierde.
 */
public final class MoneyAmounts {

    private MoneyAmounts() {
    }

    /** 12.50 EUR → 1250. */
    public static long toMinorUnits(BigDecimal amount, String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        try {
            return amount.setScale(digits, RoundingMode.UNNECESSARY).movePointRight(digits).longValueExact();
        } catch (ArithmeticException tooPrecise) {
            throw new IllegalArgumentException(
                    "El importe " + amount + " tiene más decimales de los que admite " + currency, tooPrecise);
        }
    }

    /** 1250 EUR → 12.50. */
    public static BigDecimal fromMinorUnits(long minor, String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        return BigDecimal.valueOf(minor, digits);
    }

    /** El importe como lo escribe una pasarela que trabaja en unidades mayores: "12.50". */
    public static String toMajorUnits(BigDecimal amount, String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        try {
            return amount.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException tooPrecise) {
            throw new IllegalArgumentException(
                    "El importe " + amount + " tiene más decimales de los que admite " + currency, tooPrecise);
        }
    }
}
