package com.openforge.aimate.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core infrastructure beans:
 *  - Virtual-Thread executor  → every blocking LLM call gets a free OS-thread-like experience
 *  - Java HttpClient          → the ONLY HTTP engine; no WebClient, no RestTemplate
 *  - Jackson ObjectMapper     → snake_case ↔ camelCase, Java 8 time, tolerant deserialization
 *
 * JDK 25 notes:
 *  - Virtual Threads are stable since JDK 21; no changes needed here.
 *  - Structured Concurrency (finalized in JDK 25) will be used in the Agent
 *    dispatch layer (ToolExecutor) — not wired here at infrastructure level.
 *  - Scoped Values (finalized in JDK 25) will carry sessionId / traceId
 *    across the agent loop without ThreadLocal pollution.
 */
@Configuration
public class AppConfig {

    /**
     * Named "agentVirtualThreadExecutor" to avoid conflicting with Spring Boot 3.5's
     * auto-configured "applicationTaskExecutor" bean (the old "taskExecutor" alias
     * was removed in 3.5).  This bean is injected explicitly where needed.
     */
    @Bean
    public ExecutorService agentVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Wire Tomcat's acceptor + worker threads to virtual threads.
     * Spring Boot 3.5 still uses TomcatProtocolHandlerCustomizer for this.
     * The spring.threads.virtual.enabled=true in application.yml triggers
     * Spring Boot's own auto-configuration, but registering this bean
     * explicitly ensures the executor is the same instance used by the agent.
     */
    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreadTomcatCustomizer(
            ExecutorService agentVirtualThreadExecutor) {
        return protocolHandler -> protocolHandler.setExecutor(agentVirtualThreadExecutor);
    }

    /**
     * Single, shared HttpClient instance.
     * - Uses the virtual-thread executor so every .send() call is handled
     *   by a virtual thread — no OS thread is parked while waiting for LLM I/O.
     * - 120 s connect timeout; per-request read timeouts are set at call site.
     */
    @Bean
    public HttpClient httpClient(ExecutorService agentVirtualThreadExecutor) {
        return HttpClient.newBuilder()
                .executor(agentVirtualThreadExecutor)
                .connectTimeout(Duration.ofSeconds(120))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Shared ObjectMapper configured for OpenAI-compatible JSON:
     *  - snake_case property names (tool_calls, finish_reason …)
     *  - ISO-8601 dates, NOT timestamps
     *  - Unknown properties silently ignored (API can add fields without breaking us)
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Password encoder used for user registration & login.
     * BCrypt is battle-tested and safe by default.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
