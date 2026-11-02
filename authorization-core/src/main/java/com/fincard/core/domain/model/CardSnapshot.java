package com.fincard.core.domain.model;

import com.fincard.core.domain.CardStatus;
import com.fincard.core.domain.CardType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CardSnapshot(
        String cardNumber,
        String cardExpiry,      // MM/yy
        CardStatus status,
        CardType cardType,
        BigDecimal balance,     // débito: saldo · crédito: deuda
        BigDecimal creditLimit, // solo crédito
        String currency
) {

    public BigDecimal available() {
        return cardType == CardType.CREDIT
                ? creditLimit.subtract(balance)
                : balance;
    }

    public LocalDate expiryDate() {
        String[] parts = cardExpiry.split("/");
        int month = Integer.parseInt(parts[0]);
        int year = 2000 + Integer.parseInt(parts[1]);
        return LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
    }

    public boolean isExpired(LocalDate today) {
        return expiryDate().isBefore(today);
    }
}
