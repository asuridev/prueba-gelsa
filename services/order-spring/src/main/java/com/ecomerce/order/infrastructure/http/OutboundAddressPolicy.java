package com.ecomerce.order.infrastructure.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.boot.http.client.JdkHttpClientBuilder;
import org.springframework.stereotype.Component;

/**
 * A dónde puede salir una llamada saliente, y el HttpClient del JDK que lo hace cumplir. Lo usan
 * todos los clientes que construye build (http-clients y pasarela de pago).
 *
 * <p>Se deniega siempre link-local, any-local, multicast y el endpoint de metadatos de la nube, más lo
 * que añada {@code outbound.denied-addresses} (CIDR separados por comas). Loopback y las redes
 * privadas se permiten: los proveedores internos viven ahí. Un CIDR mal escrito impide arrancar.
 *
 * <p>Es defensa en profundidad: el filtro mira la primera dirección que resuelve el host, y el cliente
 * vuelve a resolver al conectar, así que no protege de un DNS rebinding.
 *
 * <p>El rechazo es {@link org.springframework.boot.http.client.FilteredHostException}, que llega
 * cruda al llamante del RestClient: NO es un fallo del proveedor (no salió nada), así que ni el
 * fallback ni el circuito ni el retry la atienden. Es un error de configuración y se propaga.
 */
@Component
public class OutboundAddressPolicy {

    private static final InetAddressFilter ALWAYS_DENIED = InetAddressFilter.of(
            "169.254.0.0/16", // link-local IPv4: el endpoint de metadatos de la nube
            "fe80::/10", // link-local IPv6
            "0.0.0.0/8", // any-local IPv4: en Linux 0.0.0.0 conecta con el propio host
            "::/128", // any-local IPv6
            "fd00:ec2::254/128" // metadatos de AWS por IPv6, dentro de la red privada fc00::/7
            ).or(InetAddressFilter.multicast());

    private final InetAddressFilter allowed;

    public OutboundAddressPolicy(@Value("${outbound.denied-addresses:}") List<String> extraDenied) {
        InetAddressFilter denied = ALWAYS_DENIED;
        List<String> extra = extraDenied.stream().map(String::trim).filter(cidr -> !cidr.isEmpty()).toList();
        if (!extra.isEmpty()) {
            denied = denied.or(InetAddressFilter.of(extra.toArray(String[]::new)));
        }
        this.allowed = InetAddressFilter.not(denied);
    }

    /**
     * El HttpClient del JDK con el filtro puesto.
     *
     * <p>HTTP/1.1 explícito, y no lo que negocie el cliente. El del JDK negocia HTTP/2 y contra un
     * servidor en claro lo intenta con {@code Upgrade: h2c}: si el otro extremo no completa el
     * upgrade, el CUERPO SE PIERDE y la llamada muere con {@code Received RST_STREAM: Stream
     * cancelled}, que el fallback traduce a "el proveedor no está disponible". El síntoma acusa al
     * proveedor y la causa está aquí — le pasa al WireMock que levanta el propio generador.
     *
     * <p>Sin seguir redirecciones, como el HttpClient del JDK a secas: los defaults de Boot las
     * siguen, y una respuesta del proveedor no decide a dónde sale la siguiente petición.
     *
     * <p>El selector que filtra es package-private en Boot: solo lo instala {@link JdkHttpClientBuilder}.
     */
    public HttpClient httpClient(Duration connectTimeout) {
        return new JdkHttpClientBuilder()
                .withCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                .build(HttpClientSettings.defaults()
                        .withRedirects(HttpRedirects.DONT_FOLLOW)
                        .withConnectTimeout(connectTimeout)
                        .withInetAddressFilter(allowed));
    }
}
