package com.fincard.core.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CardAuthorizationRequest(
        @NotBlank @Size(min = 13, max = 19) @Pattern(regexp = "[0-9]+")
        String cardNumber,
        @NotBlank @Pattern(regexp = "^(0[1-9]|1[0-2])\\/[0-9]{2}$")
        String cardExpiry,
        @NotNull @DecimalMin("0.01")
        BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3)
        String currency,
        @NotBlank @Size(max = 40)
        String merchantId,
        @Size(max = 120)
        String merchantName
) {
}
