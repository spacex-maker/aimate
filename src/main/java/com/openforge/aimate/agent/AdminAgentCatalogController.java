package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AdminAgentCatalogController {

    private final AgentCatalogService agentCatalogService;
    private final SystemConfigService systemConfigService;

    @GetMapping("/careers")
    public List<Map<String, Object>> careers() {
        return agentCatalogService.listCareers(false);
    }

    @PostMapping("/careers")
    public Map<String, Object> createCareer(@RequestBody AgentCatalogService.CareerBody body) {
        return wrap(() -> agentCatalogService.saveCareer(null, body));
    }

    @PutMapping("/careers/{id}")
    public Map<String, Object> updateCareer(@PathVariable Long id, @RequestBody AgentCatalogService.CareerBody body) {
        return wrap(() -> agentCatalogService.saveCareer(id, body));
    }

    @GetMapping("/catalog")
    public List<Map<String, Object>> catalog() {
        return agentCatalogService.listCatalog(false);
    }

    @PostMapping("/catalog")
    public Map<String, Object> createCatalog(@RequestBody AgentCatalogService.CatalogBody body) {
        return wrap(() -> agentCatalogService.saveCatalog(null, body));
    }

    @PutMapping("/catalog/{id}")
    public Map<String, Object> updateCatalog(@PathVariable Long id, @RequestBody AgentCatalogService.CatalogBody body) {
        return wrap(() -> agentCatalogService.saveCatalog(id, body));
    }

    @GetMapping("/tier-models")
    public List<Map<String, Object>> tierModels() {
        return agentCatalogService.listTierModels();
    }

    @PutMapping("/tier-models/{tier}")
    public List<Map<String, Object>> replaceTierModels(
            @PathVariable String tier,
            @RequestBody List<AgentCatalogService.TierModelBody> body
    ) {
        try {
            return agentCatalogService.replaceTierModels(AgentTier.valueOf(tier.trim().toUpperCase()), body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/prompt-model")
    public Map<String, Object> getPromptModel() {
        return Map.of(
                "systemModelId",
                systemConfigService.get(AgentPromptService.CFG_PROMPT_MODEL_ID).orElse(null)
        );
    }

    @PutMapping("/prompt-model")
    public Map<String, Object> setPromptModel(@RequestBody Map<String, Object> body) {
        Object id = body.get("systemModelId");
        if (id == null) {
            systemConfigService.delete(AgentPromptService.CFG_PROMPT_MODEL_ID);
        } else {
            systemConfigService.put(AgentPromptService.CFG_PROMPT_MODEL_ID, String.valueOf(id));
        }
        return getPromptModel();
    }

    private Map<String, Object> wrap(java.util.concurrent.Callable<Map<String, Object>> c) {
        try {
            return c.call();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
