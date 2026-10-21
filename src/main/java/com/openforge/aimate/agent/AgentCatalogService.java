package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentCareer;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.domain.AgentTierModel;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentTierModelRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentCatalogService {

    private final AgentCareerRepository careerRepository;
    private final AgentCatalogRepository catalogRepository;
    private final AgentTierModelRepository tierModelRepository;
    private final SystemModelRepository systemModelRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCareers(boolean enabledOnly) {
        List<AgentCareer> list = enabledOnly
                ? careerRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
                : careerRepository.findAllByOrderBySortOrderAscIdAsc();
        return list.stream().map(this::careerView).toList();
    }

    @Transactional
    public Map<String, Object> saveCareer(Long id, CareerBody body) {
        AgentCareer c = id != null
                ? careerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("职业不存在"))
                : new AgentCareer();
        if (!StringUtils.hasText(body.code()) || !StringUtils.hasText(body.name())) {
            throw new IllegalArgumentException("code 与 name 必填");
        }
        careerRepository.findByCodeIgnoreCase(body.code().trim()).ifPresent(ex -> {
            if (id == null || !ex.getId().equals(id)) {
                throw new IllegalArgumentException("职业 code 已存在");
            }
        });
        c.setCode(body.code().trim().toUpperCase());
        c.setName(body.name().trim());
        c.setDescription(body.description());
        if (body.sortOrder() != null) c.setSortOrder(body.sortOrder());
        if (body.enabled() != null) c.setEnabled(body.enabled());
        return careerView(careerRepository.save(c));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCatalog(boolean enabledOnly) {
        List<AgentCatalog> list = enabledOnly
                ? catalogRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
                : catalogRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, AgentCareer> careers = new LinkedHashMap<>();
        careerRepository.findAll().forEach(x -> careers.put(x.getId(), x));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentCatalog cat : list) {
            out.add(catalogView(cat, careers.get(cat.getCareerId())));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> saveCatalog(Long id, CatalogBody body) {
        AgentCatalog c = id != null
                ? catalogRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在"))
                : new AgentCatalog();
        if (body.careerId() == null || body.tier() == null || !StringUtils.hasText(body.name())) {
            throw new IllegalArgumentException("careerId、tier、name 必填");
        }
        AgentCareer career = careerRepository.findById(body.careerId())
                .orElseThrow(() -> new IllegalArgumentException("职业不存在"));
        AgentTier tier = AgentTier.valueOf(body.tier().trim().toUpperCase());
        catalogRepository.findByCareerIdAndTier(career.getId(), tier).ifPresent(ex -> {
            if (id == null || !ex.getId().equals(id)) {
                throw new IllegalArgumentException("该职业等级已存在商品");
            }
        });
        c.setCareerId(career.getId());
        c.setTier(tier);
        c.setName(body.name().trim());
        c.setDescription(body.description());
        c.setPersonaTemplate(body.personaTemplate());
        c.setDefaultAvatarUrl(body.defaultAvatarUrl());
        c.setDefaultGender(body.defaultGender());
        if (body.priceDayPoints() != null) c.setPriceDayPoints(body.priceDayPoints());
        if (body.priceMonthPoints() != null) c.setPriceMonthPoints(body.priceMonthPoints());
        if (body.priceYearPoints() != null) c.setPriceYearPoints(body.priceYearPoints());
        if (body.soulPriceDayPoints() != null) c.setSoulPriceDayPoints(body.soulPriceDayPoints());
        if (body.soulPriceMonthPoints() != null) c.setSoulPriceMonthPoints(body.soulPriceMonthPoints());
        if (body.soulPriceYearPoints() != null) c.setSoulPriceYearPoints(body.soulPriceYearPoints());
        if (body.enabled() != null) c.setEnabled(body.enabled());
        if (body.sortOrder() != null) c.setSortOrder(body.sortOrder());
        return catalogView(catalogRepository.save(c), career);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTierModels() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentTierModel tm : tierModelRepository.findAllByOrderByTierAscSortOrderAscIdAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tm.getId());
            m.put("tier", tm.getTier().name());
            m.put("systemModelId", tm.getSystemModelId());
            m.put("isDefault", tm.isDefaultModel());
            m.put("sortOrder", tm.getSortOrder());
            systemModelRepository.findById(tm.getSystemModelId()).ifPresent(sm -> {
                m.put("modelDisplayName", sm.getDisplayName());
                m.put("modelId", sm.getModelId());
            });
            out.add(m);
        }
        return out;
    }

    @Transactional
    public List<Map<String, Object>> replaceTierModels(AgentTier tier, List<TierModelBody> items) {
        tierModelRepository.deleteByTier(tier);
        if (items != null) {
            boolean hasDefault = false;
            int order = 0;
            for (TierModelBody item : items) {
                SystemModel sm = systemModelRepository.findById(item.systemModelId())
                        .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + item.systemModelId()));
                AgentTierModel row = new AgentTierModel();
                row.setTier(tier);
                row.setSystemModelId(sm.getId());
                boolean def = item.isDefault() != null && item.isDefault();
                if (def) hasDefault = true;
                row.setDefaultModel(def);
                row.setSortOrder(item.sortOrder() != null ? item.sortOrder() : order++);
                tierModelRepository.save(row);
            }
            if (!hasDefault && !items.isEmpty()) {
                List<AgentTierModel> saved = tierModelRepository.findByTierOrderBySortOrderAscIdAsc(tier);
                if (!saved.isEmpty()) {
                    AgentTierModel first = saved.get(0);
                    first.setDefaultModel(true);
                    tierModelRepository.save(first);
                }
            }
        }
        return listTierModels().stream().filter(m -> tier.name().equals(m.get("tier"))).toList();
    }

    private Map<String, Object> careerView(AgentCareer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("sortOrder", c.getSortOrder());
        m.put("enabled", c.isEnabled());
        return m;
    }

    private Map<String, Object> catalogView(AgentCatalog c, AgentCareer career) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("careerId", c.getCareerId());
        m.put("careerName", career != null ? career.getName() : null);
        m.put("tier", c.getTier().name());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("personaTemplate", c.getPersonaTemplate());
        m.put("defaultAvatarUrl", c.getDefaultAvatarUrl());
        m.put("defaultGender", c.getDefaultGender());
        m.put("priceDayPoints", c.getPriceDayPoints());
        m.put("priceMonthPoints", c.getPriceMonthPoints());
        m.put("priceYearPoints", c.getPriceYearPoints());
        m.put("soulPriceDayPoints", c.getSoulPriceDayPoints());
        m.put("soulPriceMonthPoints", c.getSoulPriceMonthPoints());
        m.put("soulPriceYearPoints", c.getSoulPriceYearPoints());
        m.put("enabled", c.isEnabled());
        m.put("sortOrder", c.getSortOrder());
        return m;
    }

    public record CareerBody(String code, String name, String description, Integer sortOrder, Boolean enabled) {}

    public record CatalogBody(
            Long careerId,
            String tier,
            String name,
            String description,
            String personaTemplate,
            String defaultAvatarUrl,
            String defaultGender,
            Long priceDayPoints,
            Long priceMonthPoints,
            Long priceYearPoints,
            Long soulPriceDayPoints,
            Long soulPriceMonthPoints,
            Long soulPriceYearPoints,
            Boolean enabled,
            Integer sortOrder
    ) {}

    public record TierModelBody(Long systemModelId, Boolean isDefault, Integer sortOrder) {}
}
