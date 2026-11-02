package com.fincard.audit.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fila de auditoría expuesta por el REST — espejo de la tabla
 * audit_transactions y del schema AuditEntry de audit-api.yaml.
 * (Nombre elegido del CONTRATO, no "Row" del mapper interno.)
 */
public record AuditEntry(
        String transactionId,
        String maskedCardNumber,
        String decision,
        String responseCode,
        String authorizationCode,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantName,
        Instant transactionTs,
        Instant receivedAt
) {
}
