package com.fincard.core.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincard.core.domain.model.CardSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Service
public class RedisCardCacheService {

    public static final String KEY_PREFIX = "fincard:card:";   // fincard:card:<PAN>
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(180);

    private static final Logger log = LoggerFactory.getLogger(RedisCardCacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisCardCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<CardSnapshot> get(String pan) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + pan);
            if (json == null) {
                return Optional.empty();          // cache MISS: no es error
            }
            return Optional.of(objectMapper.readValue(json, CardSnapshot.class));
        } catch (Exception e) {
            log.warn("Redis read failed for card {}: {}", mask(pan), e.getMessage());
            return Optional.empty();              // degradar a Oracle, nunca romper
        }
    }

    public void put(CardSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redis.opsForValue().set(KEY_PREFIX + snapshot.cardNumber(), json, DEFAULT_TTL);
        } catch (Exception e) {
            log.warn("Redis write-through failed for card {}: {}", mask(snapshot.cardNumber()), e.getMessage());
        }
    }

    public void evict(String pan) {
        try {
            redis.delete(KEY_PREFIX + pan);
        } catch (Exception e) {
            log.warn("Redis evict failed for card {}: {}", mask(pan), e.getMessage());
        }
    }

    public void updateAfterCharge(String pan, BigDecimal newBalance) {
        get(pan).ifPresent(current ->
                put(new CardSnapshot(
                        current.cardNumber(), current.cardExpiry(),
                        current.status(), current.cardType(),
                        newBalance, current.creditLimit(), current.currency())
                )
        );
    }

    private String mask(String pan) {
        if (pan == null || pan.length() < 8) {
            return "***";
        }
        return pan.substring(0, 4) + "****" + pan.substring(pan.length() - 4);
    }
}
