package com.openforge.aimate.knowledge;

import com.openforge.aimate.domain.AgentCatalogKnowledge;
import com.openforge.aimate.domain.KbCategory;
import com.openforge.aimate.domain.KnowledgeBase;
import com.openforge.aimate.domain.UserAgentKnowledge;
import com.openforge.aimate.repository.AgentCatalogKnowledgeRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.KbCategoryRepository;
import com.openforge.aimate.repository.KnowledgeBaseRepository;
import com.openforge.aimate.repository.UserAgentKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OfficialKnowledgeService {

    private final KbCategoryRepository categoryRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentCatalogKnowledgeRepository catalogKnowledgeRepository;
    private final AgentCatalogRepository catalogRepository;
    private final UserAgentKnowledgeRepository userAgentKnowledgeRepository;

    /* ---------- categories ---------- */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCategoryTree(boolean enabledOnly) {
        List<KbCategory> all = enabledOnly
                ? categoryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
                : categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<KbCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        return buildTree(byParent, 0L, 0);
    }

    @Transactional
    public Map<String, Object> saveCategory(Long id, CategoryBody body) {
        if (!StringUtils.hasText(body.code()) || !StringUtils.hasText(body.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code、name 必填");
        }
        String code = body.code().trim().toUpperCase();
        KbCategory row = id != null
                ? categoryRepository.findById(id).orElseThrow(() -> notFound("分类不存在"))
                : new KbCategory();
        categoryRepository.findByCode(code).ifPresent(ex -> {
            if (id == null || !ex.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类 code 已存在");
            }
        });
        Long parentId = body.parentId();
        if (parentId != null) {
            if (id != null && Objects.equals(parentId, id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能将自己设为父分类");
            }
            KbCategory parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> notFound("父分类不存在"));
            if (id != null && isDescendant(id, parent.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能挂到自己的子分类下");
            }
        }
        row.setParentId(parentId);
        row.setCode(code);
        row.setName(body.name().trim());
        row.setDescription(trimTo(body.description(), 512));
        if (body.sortOrder() != null) row.setSortOrder(body.sortOrder());
        if (body.enabled() != null) row.setEnabled(body.enabled());
        return categoryView(categoryRepository.save(row), 0);
    }

    /* ---------- knowledge bases ---------- */

    @Transactional(readOnly = true)
    public Map<String, Object> pageKnowledge(
            Long categoryId,
            Boolean enabled,
            String q,
            int page,
            int size
    ) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        // 选中分类时：含该节点及其所有子孙挂载的知识
        List<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = collectSelfAndDescendantIds(categoryId);
        }
        Page<KnowledgeBase> result;
        if (categoryIds != null && categoryIds.size() > 1) {
            final List<Long> catIds = categoryIds;
            final Boolean enabledFilter = enabled;
            final String qFilter = q;
            // 多分类：含子分类挂载的知识，内存筛选后分页
            List<KnowledgeBase> all = knowledgeBaseRepository.findAll().stream()
                    .filter(k -> catIds.contains(k.getCategoryId()))
                    .filter(k -> enabledFilter == null || k.isEnabled() == enabledFilter)
                    .filter(k -> matchQ(k, qFilter))
                    .sorted((a, b) -> {
                        int d = Integer.compare(a.getSortOrder(), b.getSortOrder());
                        return d != 0 ? d : Long.compare(a.getId(), b.getId());
                    })
                    .toList();
            int from = Math.min(p * s, all.size());
            int to = Math.min(from + s, all.size());
            List<Map<String, Object>> items = all.subList(from, to).stream().map(this::kbView).toList();
            int totalPages = s == 0 ? 0 : (int) Math.ceil(all.size() / (double) s);
            return pageResult(items, p, s, all.size(), totalPages);
        }
        Long singleCat = categoryIds != null && categoryIds.size() == 1 ? categoryIds.get(0) : categoryId;
        result = knowledgeBaseRepository.search(singleCat, enabled, blankToNull(q), PageRequest.of(p, s));
        List<Map<String, Object>> items = result.getContent().stream().map(this::kbView).toList();
        return pageResult(items, p, s, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getKnowledge(Long id) {
        return kbView(knowledgeBaseRepository.findById(id).orElseThrow(() -> notFound("知识库不存在")));
    }

    @Transactional
    public Map<String, Object> saveKnowledge(Long id, KnowledgeBody body) {
        if (body.categoryId() == null || !StringUtils.hasText(body.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId、name 必填");
        }
        categoryRepository.findById(body.categoryId())
                .orElseThrow(() -> notFound("挂载分类不存在"));
        KnowledgeBase row = id != null
                ? knowledgeBaseRepository.findById(id).orElseThrow(() -> notFound("知识库不存在"))
                : new KnowledgeBase();
        if (id == null) {
            row.setPublicId(newPublicId());
        }
        if (StringUtils.hasText(body.code())) {
            String code = body.code().trim().toUpperCase();
            knowledgeBaseRepository.findByCode(code).ifPresent(ex -> {
                if (id == null || !ex.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库 code 已存在");
                }
            });
            row.setCode(code);
        } else if (id == null) {
            row.setCode(null);
        }
        row.setCategoryId(body.categoryId());
        row.setName(body.name().trim());
        row.setSummary(trimTo(body.summary(), 1024));
        row.setContent(body.content());
        row.setTags(trimTo(body.tags(), 512));
        if (body.sortOrder() != null) row.setSortOrder(body.sortOrder());
        if (body.enabled() != null) row.setEnabled(body.enabled());
        return kbView(knowledgeBaseRepository.save(row));
    }

    /* ---------- catalog ↔ knowledge ---------- */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCatalogKnowledge(Long catalogId) {
        catalogRepository.findById(catalogId).orElseThrow(() -> notFound("商品不存在"));
        return briefsForCatalog(catalogId);
    }

    @Transactional
    public List<Map<String, Object>> replaceCatalogKnowledge(Long catalogId, List<Long> knowledgeBaseIds) {
        catalogRepository.findById(catalogId).orElseThrow(() -> notFound("商品不存在"));
        catalogKnowledgeRepository.deleteByCatalogId(catalogId);
        catalogKnowledgeRepository.flush();
        if (knowledgeBaseIds != null) {
            for (Long kbId : knowledgeBaseIds.stream().filter(Objects::nonNull).distinct().toList()) {
                KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                        .orElseThrow(() -> notFound("知识库不存在: " + kbId));
                if (!kb.isEnabled()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库未启用: " + kb.getPublicId());
                }
                AgentCatalogKnowledge link = new AgentCatalogKnowledge();
                link.setCatalogId(catalogId);
                link.setKnowledgeBaseId(kb.getId());
                catalogKnowledgeRepository.save(link);
            }
        }
        return briefsForCatalog(catalogId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Map<String, Object>>> briefsByCatalogIds(Collection<Long> catalogIds) {
        Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (catalogIds == null || catalogIds.isEmpty()) return out;
        List<AgentCatalogKnowledge> links = catalogKnowledgeRepository.findByCatalogIdIn(catalogIds);
        Map<Long, KnowledgeBase> kbIndex = knowledgeBaseRepository.findByIdIn(
                links.stream().map(AgentCatalogKnowledge::getKnowledgeBaseId).distinct().toList()
        ).stream().collect(Collectors.toMap(KnowledgeBase::getId, x -> x, (a, b) -> a));
        for (AgentCatalogKnowledge link : links) {
            KnowledgeBase kb = kbIndex.get(link.getKnowledgeBaseId());
            if (kb == null) continue;
            out.computeIfAbsent(link.getCatalogId(), k -> new ArrayList<>()).add(kbBrief(kb));
        }
        return out;
    }

    /** 招聘时：把官方商品绑定的知识库快照到用户 Agent */
    @Transactional
    public void bindCatalogKnowledgeToUserAgent(Long catalogId, Long userAgentId) {
        if (catalogId == null || userAgentId == null) return;
        userAgentKnowledgeRepository.deleteByUserAgentId(userAgentId);
        for (AgentCatalogKnowledge link : catalogKnowledgeRepository.findByCatalogIdOrderByIdAsc(catalogId)) {
            UserAgentKnowledge row = new UserAgentKnowledge();
            row.setUserAgentId(userAgentId);
            row.setKnowledgeBaseId(link.getKnowledgeBaseId());
            userAgentKnowledgeRepository.save(row);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUserAgentKnowledge(Long userAgentId) {
        List<Long> ids = userAgentKnowledgeRepository.findByUserAgentIdOrderByIdAsc(userAgentId).stream()
                .map(UserAgentKnowledge::getKnowledgeBaseId)
                .toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, KnowledgeBase> index = knowledgeBaseRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, x -> x, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : ids) {
            KnowledgeBase kb = index.get(id);
            if (kb != null && kb.isEnabled()) out.add(kbBrief(kb));
        }
        return out;
    }

    /* ---------- helpers ---------- */

    private List<Map<String, Object>> briefsForCatalog(Long catalogId) {
        List<Long> ids = catalogKnowledgeRepository.findByCatalogIdOrderByIdAsc(catalogId).stream()
                .map(AgentCatalogKnowledge::getKnowledgeBaseId)
                .toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, KnowledgeBase> index = knowledgeBaseRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, x -> x, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : ids) {
            KnowledgeBase kb = index.get(id);
            if (kb != null) out.add(kbBrief(kb));
        }
        return out;
    }

    private List<Map<String, Object>> buildTree(Map<Long, List<KbCategory>> byParent, long parentKey, int depth) {
        List<KbCategory> kids = byParent.getOrDefault(parentKey, List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (KbCategory c : kids) {
            Map<String, Object> node = categoryView(c, depth);
            node.put("children", buildTree(byParent, c.getId(), depth + 1));
            out.add(node);
        }
        return out;
    }

    private boolean isDescendant(Long ancestorId, Long nodeId) {
        Long cur = nodeId;
        int guard = 0;
        while (cur != null && guard++ < 64) {
            if (Objects.equals(cur, ancestorId)) return true;
            cur = categoryRepository.findById(cur).map(KbCategory::getParentId).orElse(null);
        }
        return false;
    }

    private List<Long> collectSelfAndDescendantIds(Long rootId) {
        List<KbCategory> all = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<Long>> children = new LinkedHashMap<>();
        for (KbCategory c : all) {
            long p = c.getParentId() == null ? 0L : c.getParentId();
            children.computeIfAbsent(p, k -> new ArrayList<>()).add(c.getId());
        }
        List<Long> out = new ArrayList<>();
        ArrayList<Long> stack = new ArrayList<>();
        stack.add(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.remove(stack.size() - 1);
            out.add(id);
            List<Long> kids = children.getOrDefault(id, List.of());
            stack.addAll(kids);
        }
        return out;
    }

    private static boolean matchQ(KnowledgeBase k, String q) {
        if (!StringUtils.hasText(q)) return true;
        String needle = q.trim().toLowerCase();
        return contains(k.getName(), needle)
                || contains(k.getSummary(), needle)
                || contains(k.getTags(), needle)
                || contains(k.getPublicId(), needle)
                || contains(k.getCode(), needle);
    }

    private static boolean contains(String hay, String needle) {
        return hay != null && hay.toLowerCase().contains(needle);
    }

    private Map<String, Object> categoryView(KbCategory c, int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("parentId", c.getParentId());
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("sortOrder", c.getSortOrder());
        m.put("enabled", c.isEnabled());
        m.put("depth", depth);
        return m;
    }

    private Map<String, Object> kbView(KnowledgeBase k) {
        Map<String, Object> m = kbBrief(k);
        m.put("content", k.getContent());
        m.put("sortOrder", k.getSortOrder());
        m.put("enabled", k.isEnabled());
        m.put("createTime", k.getCreateTime());
        m.put("updateTime", k.getUpdateTime());
        categoryRepository.findById(k.getCategoryId()).ifPresent(cat -> {
            m.put("categoryName", cat.getName());
            m.put("categoryCode", cat.getCode());
        });
        m.put("linkedCatalogCount", catalogKnowledgeRepository.countByKnowledgeBaseId(k.getId()));
        return m;
    }

    private Map<String, Object> kbBrief(KnowledgeBase k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", k.getId());
        m.put("publicId", k.getPublicId());
        m.put("categoryId", k.getCategoryId());
        m.put("code", k.getCode());
        m.put("name", k.getName());
        m.put("summary", k.getSummary());
        m.put("tags", k.getTags());
        m.put("enabled", k.isEnabled());
        return m;
    }

    private static Map<String, Object> pageResult(List<?> items, int page, int size, long total, int totalPages) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", items);
        m.put("page", page);
        m.put("size", size);
        m.put("total", total);
        m.put("totalPages", totalPages);
        return m;
    }

    private static String newPublicId() {
        return "kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String trimTo(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }

    public record CategoryBody(
            Long parentId,
            String code,
            String name,
            String description,
            Integer sortOrder,
            Boolean enabled
    ) {}

    public record KnowledgeBody(
            Long categoryId,
            String code,
            String name,
            String summary,
            String content,
            String tags,
            Integer sortOrder,
            Boolean enabled
    ) {}

    public record CatalogKnowledgeBody(List<Long> knowledgeBaseIds) {}
}
