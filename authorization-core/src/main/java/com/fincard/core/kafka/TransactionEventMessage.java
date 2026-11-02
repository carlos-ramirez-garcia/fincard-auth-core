package com.fincard.core.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Espejo 1:1 del JSON del contracto: docs/events/transaction-events.md.
 * NO es un DTO HTTP (no sale por REST) — es el payload del evento Kafka.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionEventMessage(
        UUID eventId,
        String transactionId,
        String maskedCardNumber,
        String decision,
        String responseCode,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantName,
        String authorizationCode,
        Instant timestamp
) {
}
