package com.jayant.payment.Sentinel_Ledger.service.impl;

import com.jayant.payment.Sentinel_Ledger.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    private static final String BUCKET_PREFIX = "rate-limit:bucket:";

    private DefaultRedisScript<List> tokenBucketScript;

    public record RateLimitResult(boolean allowed, long remainingTokens) {}

    private DefaultRedisScript<List> getScript() {
        if (tokenBucketScript == null) {
            DefaultRedisScript<List> script = new DefaultRedisScript<>();
            script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
            script.setResultType(List.class);
            tokenBucketScript = script;
        }
        return tokenBucketScript;
    }

    public RateLimitResult tryConsume(String key) {
        String bucketKey = BUCKET_PREFIX + key;
        long now = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(
                getScript(),
                List.of(bucketKey),
                String.valueOf(rateLimitProperties.getCapacity()),
                String.valueOf(rateLimitProperties.getRefillTokens()),
                String.valueOf(rateLimitProperties.getRefillPeriodSeconds()),
                String.valueOf(now),
                "1"
        );

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        return new RateLimitResult(allowed, remaining);
    }
}