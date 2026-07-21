package com.jayant.payment.Sentinel_Ledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitProperties {
    private boolean enabled;
    private long capacity;
    private long refillTokens;
    private long refillPeriodSeconds;
}