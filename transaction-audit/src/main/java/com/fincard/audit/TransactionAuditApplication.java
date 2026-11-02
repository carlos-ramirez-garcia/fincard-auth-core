package com.fincard.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FinCard Tech - Transaction Audit.
 *
 * Consume transaction-events (Kafka), persiste auditoria y dispara
 * notificaciones. Consumidor desacoplado del core: si este servicio cae,
 * las autorizaciones siguen funcionando (los eventos quedan pendientes
 * en el outbox del core y se reintentan).
 */
@SpringBootApplication
public class TransactionAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionAuditApplication.class, args);
    }
}
