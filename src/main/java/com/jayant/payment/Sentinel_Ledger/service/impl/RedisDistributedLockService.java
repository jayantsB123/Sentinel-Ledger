package com.jayant.payment.Sentinel_Ledger.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:idempotency:";

    // Lua script for safe unlock: only delete the key if the value matches OUR lock token.
    // This prevents Thread A from accidentally releasing a lock that Thread B now owns
    // (e.g. if A's lock expired due to TTL and B acquired a new one in between).
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('del', KEYS[1]) " +
                    "else " +
                    "  return 0 " +
                    "end";

    public String tryLock(String idempotencyKey, Duration ttl) {
        String lockKey = LOCK_PREFIX + idempotencyKey;
        String lockToken = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, ttl);

        return Boolean.TRUE.equals(acquired) ? lockToken : null;
    }

    public void unlock(String idempotencyKey, String lockToken) {
        String lockKey = LOCK_PREFIX + idempotencyKey;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        redisTemplate.execute(script, Collections.singletonList(lockKey), lockToken);
    }
}