package com.openforge.aimate.apikey;

import com.openforge.aimate.domain.UserApiKey;
import com.openforge.aimate.domain.UserApiKey.KeyType;
import com.openforge.aimate.repository.UserApiKeyRepository;
import com.openforge.aimate.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD for user-owned API keys.
 *
 * All endpoints are scoped to a userId path variable.
 * In a future version this will be replaced with JWT-extracted userId.
 *
 * Base path: /api/users/{userId}/api-keys
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/api-keys")
public class UserApiKeyController {

    private final UserApiKeyRepository keyRepo;
    private final UserRepository       userRepo;

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record ApiKeyRequest(
            @NotBlank @Size(max = 64)  String provider,
            @NotNull                   KeyType keyType,
                                       String label,
            @NotBlank @Size(max = 512) String keyValue,
                                       String baseUrl,
                                       String model,
                                       boolean isDefault
    ) {}

    public record ApiKeyResponse(
            Long    id,
            String  provider,
            KeyType keyType,
            String  label,
            String  maskedKey,   // only last 6 chars visible
            String  baseUrl,
            String  model,
            boolean isDefault,
            boolean isActive,
            String  createTime
    ) {}

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping
    public List<ApiKeyResponse> list(@PathVariable Long userId) {
        return keyRepo.findByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiKeyResponse> create(
            @PathVariable Long userId,
            @Valid @RequestBody ApiKeyRequest req) {

        var user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        // If this key is set as default, clear other defaults for same slot
        if (req.isDefault()) {
            clearDefaults(userId, req.provider(), req.keyType());
        }

        UserApiKey key = new UserApiKey();
        key.setUser(user);
        key.setProvider(req.provider().toLowerCase().trim());
        key.setKeyType(req.keyType());
        key.setLabel(req.label());
        key.setKeyValue(req.keyValue().trim());
        key.setBaseUrl(req.baseUrl());
        key.setModel(req.model());
        key.setDefault(req.isDefault());
        key.setActive(true);

        return ResponseEntity.ok(toResponse(keyRepo.save(key)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> update(
            @PathVariable Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ApiKeyRequest req) {

        UserApiKey key = keyRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Key 不存在"));

        if (req.isDefault()) {
            clearDefaults(userId, req.provider(), req.keyType());
        }

        key.setProvider(req.provider().toLowerCase().trim());
        key.setKeyType(req.keyType());
        key.setLabel(req.label());
        if (!"***unchanged***".equals(req.keyValue())) {
            key.setKeyValue(req.keyValue().trim());
        }
        key.setBaseUrl(req.baseUrl());
        key.setModel(req.model());
        key.setDefault(req.isDefault());

        return ResponseEntity.ok(toResponse(keyRepo.save(key)));
    }

    // ── Set default ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/set-default")
    public ResponseEntity<ApiKeyResponse> setDefault(
            @PathVariable Long userId,
            @PathVariable Long id) {

        UserApiKey key = keyRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Key 不存在"));

        clearDefaults(userId, key.getProvider(), key.getKeyType());
        key.setDefault(true);
        return ResponseEntity.ok(toResponse(keyRepo.save(key)));
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long userId,
            @PathVariable Long id) {

        UserApiKey key = keyRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Key 不存在"));
        key.setActive(false);
        keyRepo.save(key);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clearDefaults(Long userId, String provider, KeyType keyType) {
        keyRepo.findByUserIdAndProviderAndKeyTypeAndIsDefaultTrueAndIsActiveTrue(userId, provider, keyType)
               .ifPresent(existing -> {
                   existing.setDefault(false);
                   keyRepo.save(existing);
               });
    }

    private ApiKeyResponse toResponse(UserApiKey k) {
        return new ApiKeyResponse(
                k.getId(),
                k.getProvider(),
                k.getKeyType(),
                k.getLabel(),
                maskKey(k.getKeyValue()),
                k.getBaseUrl(),
                k.getModel(),
                k.isDefault(),
                k.isActive(),
                k.getCreateTime() != null ? k.getCreateTime().toString() : null
        );
    }

    /** Show only last 6 characters of the key: sk-***...abc123 */
    private String maskKey(String key) {
        if (key == null || key.length() <= 6) return "******";
        return "sk-***..." + key.substring(key.length() - 6);
    }
}
