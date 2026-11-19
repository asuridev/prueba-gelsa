package com.ecomerce.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PaymentApplication {

    static {
        // Sin reintentos ocultos del HttpClient del JDK (ver PaymentGatewayHttpConfig).
        System.setProperty("jdk.httpclient.redirects.retrylimit", "1");
    }

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
