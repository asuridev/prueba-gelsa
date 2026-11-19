package com.ecomerce.payment.infrastructure.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de la pasarela. Los valores son dato de despliegue: en producción la credencial y
 * el secreto de firma vienen del entorno sin default (las variables están en
 * parameters/production/payments.yaml), y en local la URL apunta a la pasarela de prueba de infra/.
 *
 * @param baseUrl                 la URL de la API de la pasarela
 * @param apiKey                  la credencial con la que se le habla
 * @param webhookSecret           el secreto con el que firma sus avisos
 * @param connectTimeout          cuánto se espera para conectar
 * @param readTimeout             cuánto se espera la respuesta; superado, la acción queda EN DUDA
 * @param noticeToleranceSeconds  la antigüedad máxima de un aviso firmado (contra la repetición)
 */
@ConfigurationProperties(prefix = "payments.gateway")
public record PaymentGatewayProperties(String baseUrl, String apiKey, String webhookSecret, Duration connectTimeout,
        Duration readTimeout, long noticeToleranceSeconds) {
}
