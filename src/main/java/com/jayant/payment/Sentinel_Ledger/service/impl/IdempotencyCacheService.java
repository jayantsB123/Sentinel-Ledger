package com.jayant.payment.Sentinel_Ledger.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.jayant.payment.Sentinel_Ledger.model.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "idempotency:cache:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public record CachedResult(String status, String fingerprint, String responseJson) {}

    public Optional<CachedResult> get(String idempotencyKey) {
        String raw = redisTemplate.opsForValue().get(CACHE_PREFIX + idempotencyKey);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(raw, CachedResult.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putProcessing(String idempotencyKey, String fingerprint) throws JsonProcessingException {
        put(idempotencyKey, new CachedResult(PaymentStatus.PROCESSING.toString(), fingerprint, null));
    }

    public void putFinal(String idempotencyKey, String fingerprint, String status, String responseJson) throws JsonProcessingException {
        put(idempotencyKey, new CachedResult(status, fingerprint, responseJson));
    }

    private void put(String idempotencyKey, CachedResult result) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(result);
        redisTemplate.opsForValue().set(CACHE_PREFIX + idempotencyKey, json, CACHE_TTL);
    }

    public void evict(String idempotencyKey) {
        redisTemplate.delete(CACHE_PREFIX + idempotencyKey);
    }
}