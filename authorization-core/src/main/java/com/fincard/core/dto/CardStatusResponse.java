package com.fincard.core.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CardStatusResponse(
        String maskedCardNumber,
        String status,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        String currency,
        boolean cacheHit
) {
}
