package com.fincard.audit.kafka;

import java.math.BigDecimal;
import java.time.Instant;

public record AuditEventMessage(
        String eventId,               // cambia en cada reintento — NO sirve para dedup
        String transactionId,         // ← régimen de idempotencia (estable entre reintentos)
        String maskedCardNumber,
        String decision,
        String declineCode,
        String responseCode,
        String authorizationCode,     // solo APPROVED
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantName,
        Instant timestamp
) {
}
