package com.fincard.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardAuthorizationResponse(
        UUID transactionId,
        String decision,
        String responseCode,
        String declineCode,
        String authorizationCode,
        BigDecimal remainingBalance,
        Instant timestamp

) {
}
