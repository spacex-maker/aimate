package com.openforge.aimate.knowledge;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
public class AdminOfficialKnowledgeController {

    private final OfficialKnowledgeService knowledgeService;

    @GetMapping("/categories/tree")
    public List<Map<String, Object>> categoryTree(
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        return knowledgeService.listCategoryTree(enabledOnly);
    }

    @PostMapping("/categories")
    public Map<String, Object> createCategory(@RequestBody OfficialKnowledgeService.CategoryBody body) {
        return knowledgeService.saveCategory(null, body);
    }

    @PutMapping("/categories/{id}")
    public Map<String, Object> updateCategory(
            @PathVariable Long id,
            @RequestBody OfficialKnowledgeService.CategoryBody body
    ) {
        return knowledgeService.saveCategory(id, body);
    }

    @GetMapping("/bases")
    public Map<String, Object> pageBases(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return knowledgeService.pageKnowledge(categoryId, enabled, q, tag, code, page, size);
    }

    @GetMapping("/bases/{id}")
    public Map<String, Object> getBase(@PathVariable Long id) {
        return knowledgeService.getKnowledge(id);
    }

    @PostMapping("/bases")
    public Map<String, Object> createBase(@RequestBody OfficialKnowledgeService.KnowledgeBody body) {
        return knowledgeService.saveKnowledge(null, body);
    }

    @PutMapping("/bases/{id}")
    public Map<String, Object> updateBase(
            @PathVariable Long id,
            @RequestBody OfficialKnowledgeService.KnowledgeBody body
    ) {
        return knowledgeService.saveKnowledge(id, body);
    }

    @GetMapping("/catalog/{catalogId}/bases")
    public List<Map<String, Object>> catalogBases(@PathVariable Long catalogId) {
        return knowledgeService.listCatalogKnowledge(catalogId);
    }

    @PutMapping("/catalog/{catalogId}/bases")
    public List<Map<String, Object>> replaceCatalogBases(
            @PathVariable Long catalogId,
            @RequestBody OfficialKnowledgeService.CatalogKnowledgeBody body
    ) {
        return knowledgeService.replaceCatalogKnowledge(
                catalogId,
                body != null ? body.knowledgeBaseIds() : List.of()
        );
    }
}
