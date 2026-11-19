package com.ecomerce.order.infrastructure.http;

import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentClientConfig {

    @Bean
    public RestClient paymentRestClient(
            @Value("${http-clients.payment.base-url}") String baseUrl,
            OutboundAddressPolicy outboundAddressPolicy,
            OAuth2AuthorizedClientManager httpClientsAuthorizedClientManager,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        // El HttpClient del JDK lo construye la política de direcciones salientes: HTTP/1.1
        // explícito (el upgrade h2c pierde el cuerpo), sin seguir redirecciones y con el filtro
        // anti-SSRF. Un host filtrado lanza FilteredHostException, que no es un fallo del
        // proveedor: ni el fallback ni el circuito la atienden.
        HttpClient httpClient = outboundAddressPolicy.httpClient(Duration.ofSeconds(5));
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(5000));
        OAuth2ClientHttpRequestInterceptor oauth2 =
                new OAuth2ClientHttpRequestInterceptor(httpClientsAuthorizedClientManager);
        oauth2.setClientRegistrationIdResolver(request -> "payment");
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(oauth2)
                .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP))
                .requestFactory(requestFactory)
                .build();
    }
}
