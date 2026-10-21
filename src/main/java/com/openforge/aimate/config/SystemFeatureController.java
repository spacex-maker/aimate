package com.openforge.aimate.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SystemFeatureController {

    private final SystemConfigService systemConfigService;

    public SystemFeatureController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/api/system/features")
    public Map<String, Object> features() {
        return Map.of("ragMemoryEnabled", systemConfigService.isRagMemoryEnabled());
    }

    @PutMapping("/api/admin/features/rag-memory")
    public Map<String, Object> setRagMemory(@RequestBody RagMemoryRequest body) {
        boolean enabled = body != null && Boolean.TRUE.equals(body.enabled());
        systemConfigService.setRagMemoryEnabled(enabled);
        return Map.of("ragMemoryEnabled", enabled);
    }

    public record RagMemoryRequest(Boolean enabled) {}
}
