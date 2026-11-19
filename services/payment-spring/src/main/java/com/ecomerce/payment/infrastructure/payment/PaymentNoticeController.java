package com.ecomerce.payment.infrastructure.payment;

import com.ecomerce.payment.application.payment.PaymentNotices;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El aviso de la pasarela. Entra sin credencial —quien llama es la pasarela, que no tiene identidad
 * aquí— y lo protege la FIRMA: se verifica sobre el cuerpo crudo antes de hacer nada. Un aviso que
 * no verifica se responde con 401 y no se consulta nada; uno que verifica tampoco decide nada: el
 * desenlace se le pregunta a la pasarela (PaymentNotices).
 */
@RestController
@RequestMapping("/webhooks/payments")
public class PaymentNoticeController {

    private static final Logger log = LoggerFactory.getLogger(PaymentNoticeController.class);

    private final PaymentNoticeVerifier verifier;
    private final PaymentNotices notices;

    public PaymentNoticeController(PaymentNoticeVerifier verifier, PaymentNotices notices) {
        this.verifier = verifier;
        this.notices = notices;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body, @RequestHeader HttpHeaders headers,
            @RequestParam Map<String, String> query) {
        Optional<String> gatewayPaymentId;
        try {
            gatewayPaymentId = verifier.verify(body == null ? new byte[0] : body, headers, query);
        } catch (InvalidPaymentNoticeException invalid) {
            log.warn("Aviso de la pasarela rechazado: {}", invalid.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        gatewayPaymentId.ifPresent(notices::onNotice);
        return ResponseEntity.ok().build();
    }
}
