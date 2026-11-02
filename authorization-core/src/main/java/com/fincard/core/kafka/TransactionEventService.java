package com.fincard.core.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincard.core.repository.EventOutboxDao;
import com.fincard.core.repository.EventOutboxDao.PendingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionEventService {

    public static final String TOPIC = "transaction-events";
    private static final int RETRY_BATCH_LIMIT = 20;

    private static final Logger log = LoggerFactory.getLogger(TransactionEventService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventOutboxDao outboxDao;
    private final ObjectMapper objectMapper;

    public TransactionEventService(KafkaTemplate<String, String> kafkaTemplate,
                                   EventOutboxDao outboxDao,
                                   ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxDao = outboxDao;
        this.objectMapper = objectMapper;
    }

    /** Reemplaza el TODO(4.4) del AuthorizationService — solo APPROVED llega aquí. */
    public void recordAndPublishApproved(String transactionId, String maskedCardNumber,
                                         BigDecimal amount, String currency,
                                         String merchantId, String merchantName,
                                         String authorizationCode) {
        var message = new TransactionEventMessage(
                UUID.randomUUID(),          // eventId NUEVO en cada intento (ver contrato)
                transactionId,              // transactionId ESTABLE
                maskedCardNumber,
                "APPROVED",
                "00",
                amount, currency,
                merchantId, merchantName,
                authorizationCode,
                Instant.now()
        );

        try {
            String payload = objectMapper.writeValueAsString(message);
            long outboxId = outboxDao.insertPending(transactionId, payload);
            tryPublish(outboxId, transactionId, payload);
        } catch (Exception e) {
            // serialización fallida: el evento ni siquiera pudo nacer — ERROR real
            log.error("Failed to serialize/record outbox event for txn {}", transactionId, e);
        }
    }

    private void tryPublish(long outboxId, String transactionId, String payload) {
        try {
            kafkaTemplate.send(TOPIC, transactionId, payload).get();   // bloquear hasta ack
            outboxDao.markSent(outboxId);
            log.info("event published for txn {} (outbox {})", transactionId, outboxId);
        } catch (Exception e) {
            // Kafka caído/slow: NO rompo — el evento já durar en outbox
            log.warn("Kafka publish failed, keeping outboxId={} PENDING for txn {}",
                    outboxId, transactionId);
        }
    }

    /** Reintento batch de pendientes (el @EnableScheduling de la Application ya está). */
    @Scheduled(fixedDelay = 10_000)
    public void retryPending() {
        var pending = outboxDao.listPending(RETRY_BATCH_LIMIT);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying {} pending outbox events", pending.size());
        for (PendingEvent event : pending) {
            tryPublish(event.outboxId(), event.transactionId(), event.payload());
        }
    }
}
