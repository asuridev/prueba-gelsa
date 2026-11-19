package com.ecomerce.payment.infrastructure.payment;

import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/**
 * Verifica un aviso de la pasarela y dice de qué cobro habla. Cada pasarela firma a su manera; la
 * implementación es la de la pasarela elegida.
 */
public interface PaymentNoticeVerifier {

    /**
     * @param body    el cuerpo CRUDO, tal como llegó: la firma se calcula sobre estos bytes, no sobre
     *                un JSON re-serializado
     * @param headers las cabeceras de la petición
     * @param query   los parámetros de la URL
     * @return el id de la pasarela del cobro del que habla el aviso, o vacío si es un aviso válido que
     *         no habla de ningún cobro
     * @throws InvalidPaymentNoticeException si la firma no verifica o el aviso es demasiado antiguo
     */
    Optional<String> verify(byte[] body, HttpHeaders headers, Map<String, String> query);
}
