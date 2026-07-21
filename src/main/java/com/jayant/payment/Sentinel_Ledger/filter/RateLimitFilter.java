package com.jayant.payment.Sentinel_Ledger.filter;


import com.jayant.payment.Sentinel_Ledger.config.RateLimitProperties;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.PaymentErrorResponseDTO;
import com.jayant.payment.Sentinel_Ledger.service.impl.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends HttpFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!rateLimitProperties.isEnabled() || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String rateLimitKey = resolveKey(request);
        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume(rateLimitKey);

        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitProperties.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(rateLimitProperties.getRefillPeriodSeconds()));
            response.setContentType("application/json");

            PaymentErrorResponseDTO errorBody = new PaymentErrorResponseDTO(
                    "RATE_LIMIT_EXCEEDED",
                    "Too many requests. Please try again later.",
                    Instant.now()
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return; // short-circuit here
        }

        chain.doFilter(request, response);
    }

    /**
     * Keying strategy: prefer an API-key/client-identifier header if present (multi-tenant
     * gateway scenario, each bank/PSP connected to Sentinel-Ledger gets its own bucket).
     * Falls back to remote IP for anonymous/dev traffic. Avoiding keying purely by idempotency
     * key cuz that would let one key's bucket be trivially exhausted by an attacker cycling keys.
     */
    private String resolveKey(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-Id");
        if (clientId != null && !clientId.isBlank()) {
            return "client:" + clientId;
        }
        return "ip:" + request.getRemoteAddr();
    }
}