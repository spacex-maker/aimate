package com.openforge.aimate.embedding;

import com.openforge.aimate.domain.UserEmbeddingModel;
import com.openforge.aimate.repository.UserEmbeddingModelRepository;
import com.openforge.aimate.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD for user-configured embedding models.
 * Base path: /api/users/{userId}/embedding-models
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/embedding-models")
public class UserEmbeddingModelController {

    private final UserEmbeddingModelRepository embRepo;
    private final UserRepository               userRepo;

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record EmbeddingModelRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 32)  String provider,
            @NotBlank @Size(max = 128) String modelName,
                                       String apiKey,
            @NotBlank @Size(max = 512) String baseUrl,
            @Min(64)                   int    dimension,
                                       Integer maxTokens,
                                       boolean isDefault
    ) {}

    public record EmbeddingModelResponse(
            Long    id,
            String  name,
            String  provider,
            String  modelName,
            String  maskedKey,
            String  baseUrl,
            int     dimension,
            String  collectionName,
            int     maxTokens,
            boolean isDefault,
            boolean isActive,
            String  createTime
    ) {}

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping
    public List<EmbeddingModelResponse> list(@PathVariable Long userId) {
        return embRepo.findByUserIdAndIsActiveTrue(userId)
                .stream().map(this::toResponse).toList();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<EmbeddingModelResponse> create(
            @PathVariable Long userId,
            @Valid @RequestBody EmbeddingModelRequest req) {

        var user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (req.isDefault()) clearDefaults(userId);

        UserEmbeddingModel m = new UserEmbeddingModel();
        m.setUser(user);
        m.setName(req.name());
        m.setProvider(req.provider().toLowerCase().trim());
        m.setModelName(req.modelName().trim());
        m.setApiKey(req.apiKey());
        m.setBaseUrl(req.baseUrl().trim());
        m.setDimension(req.dimension());
        m.setCollectionName(UserEmbeddingModel.deriveCollectionName(req.modelName(), req.dimension()));
        m.setMaxTokens(req.maxTokens() != null ? req.maxTokens() : 8192);
        m.setDefault(req.isDefault());
        m.setActive(true);

        return ResponseEntity.ok(toResponse(embRepo.save(m)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<EmbeddingModelResponse> update(
            @PathVariable Long userId,
            @PathVariable Long id,
            @Valid @RequestBody EmbeddingModelRequest req) {

        UserEmbeddingModel m = embRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在"));

        if (req.isDefault()) clearDefaults(userId);

        m.setName(req.name());
        m.setProvider(req.provider().toLowerCase().trim());
        m.setModelName(req.modelName().trim());
        if (req.apiKey() != null && !req.apiKey().isBlank()) m.setApiKey(req.apiKey());
        m.setBaseUrl(req.baseUrl().trim());
        m.setDimension(req.dimension());
        m.setCollectionName(UserEmbeddingModel.deriveCollectionName(req.modelName(), req.dimension()));
        if (req.maxTokens() != null) m.setMaxTokens(req.maxTokens());
        m.setDefault(req.isDefault());

        return ResponseEntity.ok(toResponse(embRepo.save(m)));
    }

    // ── Set default ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/set-default")
    public ResponseEntity<EmbeddingModelResponse> setDefault(
            @PathVariable Long userId,
            @PathVariable Long id) {

        UserEmbeddingModel m = embRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在"));

        clearDefaults(userId);
        m.setDefault(true);
        return ResponseEntity.ok(toResponse(embRepo.save(m)));
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long userId,
            @PathVariable Long id) {

        UserEmbeddingModel m = embRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在"));
        m.setActive(false);
        embRepo.save(m);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clearDefaults(Long userId) {
        embRepo.findByUserIdAndIsDefaultTrueAndIsActiveTrue(userId)
               .ifPresent(existing -> {
                   existing.setDefault(false);
                   embRepo.save(existing);
               });
    }

    private EmbeddingModelResponse toResponse(UserEmbeddingModel m) {
        return new EmbeddingModelResponse(
                m.getId(),
                m.getName(),
                m.getProvider(),
                m.getModelName(),
                m.getApiKey() != null ? "***" + m.getApiKey().substring(Math.max(0, m.getApiKey().length() - 4)) : null,
                m.getBaseUrl(),
                m.getDimension(),
                m.getCollectionName(),
                m.getMaxTokens(),
                m.isDefault(),
                m.isActive(),
                m.getCreateTime() != null ? m.getCreateTime().toString() : null
        );
    }
}
