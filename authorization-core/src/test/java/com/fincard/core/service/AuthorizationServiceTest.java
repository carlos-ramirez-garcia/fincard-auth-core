package com.fincard.core.service;

import com.fincard.core.service.AuthorizationService;
import com.fincard.core.cache.RedisCardCacheService;
import com.fincard.core.domain.CardStatus;
import com.fincard.core.domain.CardType;
import com.fincard.core.domain.exception.CardNotFoundException;
import com.fincard.core.domain.model.CardSnapshot;
import com.fincard.core.dto.CardAuthorizationRequest;
import com.fincard.core.dto.CardAuthorizationResponse;
import com.fincard.core.kafka.TransactionEventService;
import com.fincard.core.repository.AuthorizationDao;
import com.fincard.core.repository.CardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fincard.core.repository.AuthorizationDao.OracleDecision;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class AuthorizationServiceTest {

    private static final String PAN = "4539148803436467";
    private static final String MASKED = "4539****6467";

    @Mock private RedisCardCacheService cache;
    @Mock private CardRepository repository;
    @Mock private AuthorizationDao dao;
    @Mock private TransactionEventService events;

    @InjectMocks
    private AuthorizationService service;

    private CardSnapshot debit(String expiry, CardStatus status, BigDecimal balance) {
        return new CardSnapshot(PAN, expiry, status, CardType.DEBIT,
                balance, BigDecimal.ZERO, "USD");
    }

    private CardAuthorizationRequest request(String amount) {
        return new CardAuthorizationRequest(PAN, "10/29",
                new BigDecimal(amount), "USD", "MERCH-TEST", "Novabase Merchant");
    }

    private OracleDecision approved(BigDecimal remaining) {
        return new OracleDecision("APPROVED", "00", null, remaining, "QPKXTE");
    }

    private OracleDecision declined(String code, String declineCode) {
        return new OracleDecision("DECLINED", code, declineCode, null, null);
    }

    @Test
    @DisplayName("APPROVED: el procedure decide, el service refresca cache y emite evento")
    void authorize_approvedCash() {
        var snapshot = debit("10/29", CardStatus.ACTIVE, new BigDecimal("42.50"));
        when(cache.get(PAN)).thenReturn(Optional.of(snapshot));
        when(dao.callAuthorizationProcedure(anyString(), eq(PAN), eq(new BigDecimal("75.00")),
                eq("USD"), eq("MERCH-TEST"), eq("Novabase Merchant"), eq(MASKED)))
                .thenReturn(approved(new BigDecimal("1775.00")));

        CardAuthorizationResponse response = service.authorize(request("75.00"));

        assertEquals("APPROVED", response.decision());
        assertEquals("00", response.responseCode());
        assertEquals(new BigDecimal("1775.00"), response.remainingBalance());
        assertEquals("QPKXTE", response.authorizationCode());

        verify(cache).updateAfterCharge(PAN, new BigDecimal("1775.00"));
        verify(events).recordAndPublishApproved(
                anyString(),
                eq(MASKED), eq(new BigDecimal("75.00")), eq("USD"),
                eq("MERCH-TEST"), eq("Novabase Merchant"), eq("QPKXTE"));
    }

    @Test
    @DisplayName("51 INSUFFICIENT_FUNDS: decline de negocio NO toca cache NI emite evento")
    void authorize_insufficientFunds() {
        var snapshot = debit("10/29", CardStatus.ACTIVE, new BigDecimal("42.50"));
        when(cache.get(PAN)).thenReturn(Optional.of(snapshot));
        when(dao.callAuthorizationProcedure(anyString(), eq(PAN),
                eq(new BigDecimal("100.00")), eq("USD"), eq("MERCH-TEST"),
                eq("Novabase Merchant"), eq(MASKED)))
                .thenReturn(declined("51", "INSUFFICIENT_FUNDS"));

        CardAuthorizationResponse response = service.authorize(request("100.00"));

        assertEquals("DECLINED", response.decision());
        assertEquals("51", response.responseCode());
        assertEquals("INSUFFICIENT_FUNDS", response.declineCode());

        verify(cache, never()).updateAfterCharge(anyString(), any());  // saldo intacto
        verifyNoInteractions(events);                                  // garantía "1 solo evento por APPROVED"
    }

    @Test
    @DisplayName("07 CARD_BLOCKED: se juzga en Java — NUNCA consulta el procedure (ni lock)")
    void authorize_blockedEarlyDecline() {
        var snapshot = debit("10/29", CardStatus.BLOCKED, new BigDecimal("500.00"));
        when(cache.get(PAN)).thenReturn(Optional.of(snapshot));
        // NO stub de dao — si el service lo llamara, Mockito lanza potencialStubbingProblem

        CardAuthorizationResponse response = service.authorize(request("50.00"));

        assertEquals("DECLINED", response.decision());
        assertEquals("07", response.responseCode());
        assertEquals("CARD_BLOCKED", response.declineCode());
        assertEquals(new BigDecimal("500.00"), response.remainingBalance());
        verifyNoInteractions(dao, events);   // 🎯 la_docs del early-decline
    }

    @Test
    @DisplayName("54 CARD_EXPIRED: expiración derivada del expiry MM/yy, decisión local")
    void authorize_expiredEarlyDecline() {
        var snapshot = debit("01/25", CardStatus.ACTIVE, new BigDecimal("1000.00"));
        when(cache.get(PAN)).thenReturn(Optional.of(snapshot));

        CardAuthorizationResponse response = service.authorize(request("50.00"));

        assertEquals("DECLINED", response.decision());
        assertEquals("54", response.responseCode());
        verifyNoInteractions(dao, events);
    }

    @Test
    @DisplayName("14 CARD_NOT_FOUND: la repo lanza → 14, sin cachear nada ni DAO")
    void authorize_cardNotFound() {
        when(cache.get(PAN)).thenReturn(Optional.empty());
        when(repository.findByPan(PAN)).thenThrow(new CardNotFoundException("card not found"));

        CardAuthorizationResponse response = service.authorize(request("50.00"));

        assertEquals("DECLINED", response.decision());
        assertEquals("14", response.responseCode());
        assertEquals("CARD_NOT_FOUND", response.declineCode());
        verify(cache, never()).put(any());   // no se ensucia el cache con fantasmas
        verifyNoInteractions(dao, events);
    }

    @Test
    @DisplayName("cache MISS: repository carga y cache.put puebla el cache")
    void authorize_cacheMissLoadsFromRepository() {
        var snapshot = debit("10/29", CardStatus.ACTIVE, new BigDecimal("1850.00"));
        when(cache.get(PAN)).thenReturn(Optional.empty());     // ❄ el cache no lo tiene
        when(repository.findByPan(PAN)).thenReturn(snapshot);  // la repo responde
        when(dao.callAuthorizationProcedure(anyString(), eq(PAN),
                eq(new BigDecimal("75.00")), eq("USD"), eq("MERCH-TEST"),
                eq("Novabase Merchant"), eq(MASKED)))
                .thenReturn(approved(new BigDecimal("1775.00")));

        service.authorize(request("75.00"));

        verify(cache).put(snapshot);          // el miss quedó "guardado para la próxima"
        verify(repository).findByPan(PAN);    // exactamente 1 ida a la repo
    }

}
