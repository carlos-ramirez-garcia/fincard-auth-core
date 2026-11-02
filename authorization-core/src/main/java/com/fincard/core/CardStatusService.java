package com.fincard.core;

import com.fincard.core.cache.RedisCardCacheService;
import com.fincard.core.domain.CardStatus;
import com.fincard.core.domain.model.CardSnapshot;
import com.fincard.core.dto.CardStatusResponse;
import com.fincard.core.repository.CardRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class CardStatusService {

    private final RedisCardCacheService cache;
    private final CardRepository repository;

    public CardStatusService(RedisCardCacheService cache, CardRepository repository) {
        this.cache = cache;
        this.repository = repository;
    }

    public CardStatusResponse getCardStatus(String pan) {
        boolean cacheHit;
        CardSnapshot snapshot = cache.get(pan).orElse(null);

        if (snapshot == null) {                    // cache MISS
            snapshot = repository.findByPan(pan);  // Oracle
            cache.put(snapshot);                   // puebla para la próxima
            cacheHit = false;
        } else {                                   // cache HIT
            cacheHit = true;
        }

        return new CardStatusResponse(
                mask(pan),
                resolveStatus(snapshot).name(),
                snapshot.balance(),
                snapshot.available(),
                snapshot.currency(),
                cacheHit
        );
    }

    private CardStatus resolveStatus(CardSnapshot snapshot) {
        if (snapshot.status() == CardStatus.ACTIVE && snapshot.isExpired(LocalDate.now())) {
            return CardStatus.EXPIRED;
        }
        return snapshot.status();
    }

    private String mask(String pan) {
        if (pan == null || pan.length() < 8) {
            return "***";
        }
        return pan.substring(0, 4) + "****" + pan.substring(pan.length() - 4);
    }
}
