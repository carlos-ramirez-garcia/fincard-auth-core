package com.fincard.core.service;

import com.fincard.core.cache.RedisCardCacheService;
import com.fincard.core.domain.CardStatus;
import com.fincard.core.domain.DeclineCode;
import com.fincard.core.domain.exception.CardNotFoundException;
import com.fincard.core.domain.model.CardSnapshot;
import com.fincard.core.dto.CardAuthorizationRequest;
import com.fincard.core.dto.CardAuthorizationResponse;
import com.fincard.core.kafka.TransactionEventService;
import com.fincard.core.repository.AuthorizationDao;
import com.fincard.core.repository.AuthorizationDao.OracleDecision;
import com.fincard.core.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final RedisCardCacheService cache;
    private final CardRepository repository;
    private final AuthorizationDao dao;
    private final TransactionEventService events;

    public AuthorizationService(RedisCardCacheService cache,
                                CardRepository repository,
                                AuthorizationDao dao, TransactionEventService events) {
        this.cache = cache;
        this.repository = repository;
        this.dao = dao;
        this.events = events;
    }

    public CardAuthorizationResponse authorize(CardAuthorizationRequest request) {
        UUID transactionId = UUID.randomUUID();
        String pan = request.cardNumber();

        CardSnapshot snapshot;
        try {
            snapshot  = loadSnapshot(pan);
        } catch (CardNotFoundException nf) {

            log.info("txn {} decline CARD_NOT_FOUND for {}",transactionId,mask(pan));
            return declined(transactionId,"14",DeclineCode.CARD_NOT_FOUND,null);
        }


        // ——— checks "optimistas" (rápidos, sin lock de Oracle) ———
        if (snapshot.status() == CardStatus.BLOCKED) {
            return declined(transactionId, "07", DeclineCode.CARD_BLOCKED, snapshot.available());
        }
        if (snapshot.isExpired(LocalDate.now())) {
            return declined(transactionId, "54", DeclineCode.CARD_EXPIRED, snapshot.available());
        }
        // NOTA: el check de FONDOS NO se hace en Java — la decisión definitiva
        //       pasa por el procedure, que es el único que puede ver el saldo
        //       fresco bajo bloqueo pesimista (anti doble-gasto).

        // ——— la decisión ARGUMENTA: una sola llamada, todo adentro del procedure ———
        OracleDecision decision = dao.callAuthorizationProcedure(
                transactionId.toString(),
                pan,
                request.amount(),
                request.currency(),
                request.merchantId(),
                request.merchantName(),
                mask(pan)
        );



            if ("APPROVED".equals(decision.decision())) {
                cache.updateAfterCharge(pan, decision.remainingBalance());
                events.recordAndPublishApproved(
                        transactionId.toString(), mask(pan), request.amount(), request.currency(),
                        request.merchantId(), request.merchantName(), decision.authorizationCode()
                );
            }


        log.info("txn {} decision={} code={}", transactionId, decision.decision(), decision.responseCode());

        return new CardAuthorizationResponse(
                transactionId,
                decision.decision(),
                decision.responseCode(),
                decision.declineCode(),
                decision.authorizationCode(),
                decision.remainingBalance(),
                Instant.now()
        );
    }

    // ——— helpers internos ———

    private CardSnapshot loadSnapshot(String pan) {
        return cache.get(pan).orElseGet(() -> {
            CardSnapshot loaded = repository.findByPan(pan);   // puede lanzar CardNotFoundException
            cache.put(loaded);
            return loaded;
        });
    }

    private CardAuthorizationResponse declined(UUID transactionId, String responseCode,
                                               DeclineCode declineCode, BigDecimal remainingBalance) {
        return new CardAuthorizationResponse(
                transactionId, "DECLINED", responseCode,
                declineCode.name(), null, remainingBalance, Instant.now()
        );
    }

    private String mask(String pan) {
        if (pan == null || pan.length() < 8) {
            return "***";
        }
        return pan.substring(0, 4) + "****" + pan.substring(pan.length() - 4);
    }
}
