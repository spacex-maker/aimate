package com.openforge.aimate.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

/**
 * Programmatic Resilience4j wiring.
 *
 * Two named instances are pre-wired, one per LLM provider:
 *   • "primaryLlm"  — e.g. GPT-4o / Claude 3.5
 *   • "fallbackLlm" — e.g. Deepseek / Qwen
 *
 * The LlmClient will try primaryLlm first; if the circuit is OPEN it
 * falls through to fallbackLlm automatically (see LlmRouter).
 */
@Configuration
public class Resilience4jConfig {

    // ── Circuit Breaker ──────────────────────────────────────────────────────

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // trip after 50 % of the last 10 calls fail
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                // treat slow calls (>60 s) as failures
                .slowCallDurationThreshold(Duration.ofSeconds(60))
                .slowCallRateThreshold(80)
                // allow 2 probe calls while HALF-OPEN
                .permittedNumberOfCallsInHalfOpenState(2)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // only IOException and RuntimeException count as failures
                .recordExceptions(IOException.class, RuntimeException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        // Force-create the two named breakers so they appear in Actuator metrics
        registry.circuitBreaker("primaryLlm");
        registry.circuitBreaker("fallbackLlm");
        return registry;
    }

    @Bean
    public CircuitBreaker primaryLlmCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("primaryLlm");
    }

    @Bean
    public CircuitBreaker fallbackLlmCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("fallbackLlm");
    }

    // ── Retry ────────────────────────────────────────────────────────────────

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                // exponential back-off: 1 s → 2 s → 4 s
                .waitDuration(Duration.ofSeconds(1))
                .retryExceptions(IOException.class)
                // 429 Too Many Requests is an IOException in our client
                .build();

        RetryRegistry registry = RetryRegistry.of(config);
        registry.retry("primaryLlm");
        registry.retry("fallbackLlm");
        return registry;
    }

    @Bean
    public Retry primaryLlmRetry(RetryRegistry registry) {
        return registry.retry("primaryLlm");
    }

    @Bean
    public Retry fallbackLlmRetry(RetryRegistry registry) {
        return registry.retry("fallbackLlm");
    }
}
