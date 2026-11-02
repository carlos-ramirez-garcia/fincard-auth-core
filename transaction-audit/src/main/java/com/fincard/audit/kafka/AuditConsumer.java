package com.fincard.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincard.audit.repository.AuditDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditConsumer {

    private static final String TOPIC = "transaction-events";
    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final AuditDao dao;
    private final ObjectMapper objectMapper;      // AUTO-configurado por Boot (con JavaTimeModule)

    public AuditConsumer(AuditDao dao, ObjectMapper objectMapper) {
        this.dao = dao;
        this.objectMapper = objectMapper;
    }


    @KafkaListener(topics = TOPIC, groupId = "fincard-audit")
    public void onEvent(String payload) {
        try {
            AuditEventMessage evt = objectMapper.readValue(payload, AuditEventMessage.class);

            if (dao.exists(evt.transactionId())) {
                log.info("duplicado ignorado txn {} (idempotencia)", evt.transactionId());
                return;
            }

            dao.insert(evt);
            log.info("txn {} decision={} auditada", evt.transactionId(), evt.decision());
        } catch (Exception e) {
            // NUNCA tragamos silencio: si insert falla, re-kick (parte de at-least-once,
            // el broker lo re-entrega porque el offset no avanza sin commit)
            log.error("fallo procesando evento: {}", payload, e);
            throw new IllegalStateException("re-kick evento a Kafka", e);
        }
    }
}
