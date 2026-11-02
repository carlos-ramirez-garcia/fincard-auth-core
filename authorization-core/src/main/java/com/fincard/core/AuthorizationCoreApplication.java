package com.fincard.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FinCard Tech - Authorization Core.
 *
 * Recibe autorizaciones REST, valida vigencia/estado/fondos (cache Redis +
 * checks optimistas), decide en Oracle via lic_process_authorization,
 * mantiene el cache fresco tras cada cargo y emite el evento a Kafka
 * transaction-events via TransactionalOutboxQueue.
 */
@SpringBootApplication
@EnableScheduling        // scheduler de reintentos del outbox
public class AuthorizationCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorizationCoreApplication.class, args);
    }
}
