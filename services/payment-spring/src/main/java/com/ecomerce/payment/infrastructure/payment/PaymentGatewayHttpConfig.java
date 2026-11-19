package com.ecomerce.payment.infrastructure.payment;

import com.ecomerce.payment.infrastructure.http.OutboundAddressPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * El cliente HTTP de la pasarela. Sin reintentos a propósito: reintentar una escritura que la
 * pasarela pudo haber hecho es cobrar dos veces. Una respuesta que no llega deja la acción en duda,
 * y eso lo resuelve el barrido preguntando.
 *
 * <p>El HttpClient del JDK lo construye la política de direcciones salientes (filtro anti-SSRF,
 * HTTP/1.1, sin seguir redirecciones). Un host filtrado lanza FilteredHostException, que NO es un
 * RestClientException: no deja la acción en duda, porque no salió nada; se propaga como error de
 * configuración.
 */
@Configuration
@EnableConfigurationProperties(PaymentGatewayProperties.class)
public class PaymentGatewayHttpConfig {

    static {
        // El HttpClient del JDK reintenta por su cuenta un GET cuya conexión se cierra sin respuesta: la consulta de
        // estado llegaría dos veces a la pasarela. Con 1 intento no hay reintento oculto (hay que fijarlo antes de que
        // el JDK cargue su cliente); el reintento es decisión del barrido.
        System.setProperty("jdk.httpclient.redirects.retrylimit", "1");
    }

    @Bean
    public RestClient paymentGatewayRestClient(PaymentGatewayProperties properties,
            OutboundAddressPolicy outboundAddressPolicy) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(outboundAddressPolicy.httpClient(properties.connectTimeout()));
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
