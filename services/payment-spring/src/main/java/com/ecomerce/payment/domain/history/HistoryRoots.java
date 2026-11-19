package com.ecomerce.payment.domain.history;

import java.util.List;

/**
 * Las raíces con historial (persistence.audit.history): el tipo con que se guardan sus entradas —el nombre del diseño,
 * contrato con los otros generadores— y sus rutas personales, con los patrones de keel-core.
 */
public final class HistoryRoots {

    public static final String PAYMENT = "Payment";

    public static final List<PersonalPath> PAYMENT_PERSONAL = List.of(
            PersonalPath.of("^customerRef$", true),
            PersonalPath.of("^refunds\\[[^\\]]*\\]\\.reason$", false));

    public static final String WALLET = "Wallet";

    public static final List<PersonalPath> WALLET_PERSONAL = List.of(
            PersonalPath.of("^customerRef$", true),
            PersonalPath.of("^paymentMethods\\[[^\\]]*\\]\\.card\\.brand$", false),
            PersonalPath.of("^paymentMethods\\[[^\\]]*\\]\\.card\\.last4$", false),
            PersonalPath.of("^paymentMethods\\[[^\\]]*\\]\\.card\\.expMonth$", false),
            PersonalPath.of("^paymentMethods\\[[^\\]]*\\]\\.card\\.expYear$", false));

    private HistoryRoots() {
    }
}
