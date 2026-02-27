package com.openforge.aimate.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Pure-Java embedding client — zero SDK dependency.
 *
 * Calls the OpenAI-compatible /embeddings endpoint and returns a float vector.
 * Runs on the caller's virtual thread; the blocking I/O cost is negligible.
 *
 * Design principle: identical to LlmClient — raw HttpClient + Jackson only.
 */
@Slf4j
@Component
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingClient {

    private final HttpClient         httpClient;
    private final ObjectMapper       objectMapper;
    private final EmbeddingProperties props;

    public EmbeddingClient(HttpClient httpClient,
                           ObjectMapper objectMapper,
                           EmbeddingProperties props) {
        this.httpClient   = httpClient;
        this.objectMapper = objectMapper;
        this.props        = props;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Embed a piece of text and return the float vector.
     *
     * The returned list has {@code props.dimensions()} elements (e.g. 1536).
     * Milvus stores it as a FloatVector field.
     *
     * @param text the text to embed (will be truncated server-side if too long)
     * @return float vector, length = {@link EmbeddingProperties#dimensions()}
     */
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Cannot embed blank text");
        }

        // Trim to a safe length to avoid exceeding model token limits
        String input = text.length() > 8000 ? text.substring(0, 8000) : text;

        EmbeddingRequest request = EmbeddingRequest.of(input, props.model(), props.dimensions());
        String body = serialize(request);

        log.debug("[Embed] → POST /embeddings model={} input-length={}", props.model(), input.length());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(props.baseUrl() + "/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Network error calling embedding API", e);
        }

        return parseResponse(response);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<Float> parseResponse(HttpResponse<String> response) {
        int    status = response.statusCode();
        String body   = response.body();

        if (status == 429) throw new EmbeddingException("Embedding API rate-limited");
        if (status < 200 || status >= 300)
            throw new EmbeddingException("Embedding API returned HTTP %d: %s".formatted(status, body));

        try {
            EmbeddingResponse resp = objectMapper.readValue(body, EmbeddingResponse.class);
            List<Float> vector = resp.firstEmbedding();
            log.debug("[Embed] ← vector dim={}", vector.size());
            return vector;
        } catch (JsonProcessingException e) {
            throw new EmbeddingException("Failed to parse embedding response: " + body, e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new EmbeddingException("Failed to serialize embedding request", e);
        }
    }

    // ── Exception ────────────────────────────────────────────────────────────

    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message) { super(message); }
        public EmbeddingException(String message, Throwable cause) { super(message, cause); }
    }
}
