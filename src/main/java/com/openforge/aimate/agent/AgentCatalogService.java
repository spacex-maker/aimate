package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentCareer;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentExecutionMode;
import com.openforge.aimate.domain.AgentIndustry;
import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.domain.AgentTierModel;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.knowledge.OfficialKnowledgeService;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentIndustryRepository;
import com.openforge.aimate.repository.AgentTierModelRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentCatalogService {

    private final AgentIndustryRepository industryRepository;
    private final AgentCareerRepository careerRepository;
    private final AgentCatalogRepository catalogRepository;
    private final AgentTierModelRepository tierModelRepository;
    private final SystemModelRepository systemModelRepository;
    private final OfficialKnowledgeService officialKnowledgeService;

    public AgentCatalogService(
            AgentIndustryRepository industryRepository,
            AgentCareerRepository careerRepository,
            AgentCatalogRepository catalogRepository,
            AgentTierModelRepository tierModelRepository,
            SystemModelRepository systemModelRepository,
            @Lazy OfficialKnowledgeService officialKnowledgeService
    ) {
        this.industryRepository = industryRepository;
        this.careerRepository = careerRepository;
        this.catalogRepository = catalogRepository;
        this.tierModelRepository = tierModelRepository;
        this.systemModelRepository = systemModelRepository;
        this.officialKnowledgeService = officialKnowledgeService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listIndustries(boolean enabledOnly) {
        List<AgentIndustry> list = enabledOnly
                ? industryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
                : industryRepository.findAllByOrderBySortOrderAscIdAsc();
        return list.stream().map(this::industryView).toList();
    }

    @Transactional
    public Map<String, Object> saveIndustry(Long id, IndustryBody body) {
        AgentIndustry c = id != null
                ? industryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("行业不存在"))
                : new AgentIndustry();
        if (!StringUtils.hasText(body.code()) || !StringUtils.hasText(body.name())) {
            throw new IllegalArgumentException("code 与 name 必填");
        }
        industryRepository.findByCodeIgnoreCase(body.code().trim()).ifPresent(ex -> {
            if (id == null || !ex.getId().equals(id)) {
                throw new IllegalArgumentException("行业 code 已存在");
            }
        });
        c.setCode(body.code().trim().toUpperCase());
        c.setName(body.name().trim());
        c.setDescription(body.description());
        if (body.sortOrder() != null) c.setSortOrder(body.sortOrder());
        if (body.enabled() != null) c.setEnabled(body.enabled());
        return industryView(industryRepository.save(c));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCareers(boolean enabledOnly) {
        List<AgentCareer> list = enabledOnly
                ? careerRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
                : careerRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, AgentIndustry> industries = industryIndex();
        return list.stream().map(c -> careerView(c, industries.get(c.getIndustryId()))).toList();
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
        AgentIndustry industry = null;
        if (body.industryId() != null) {
            industry = industryRepository.findById(body.industryId())
                    .orElseThrow(() -> new IllegalArgumentException("行业不存在"));
            c.setIndustryId(industry.getId());
        } else if (body.clearIndustry() != null && body.clearIndustry()) {
            c.setIndustryId(null);
        }
        c.setCode(body.code().trim().toUpperCase());
        c.setName(body.name().trim());
        c.setDescription(body.description());
        if (StringUtils.hasText(body.executionMode())) {
            c.setExecutionMode(AgentExecutionMode.valueOf(body.executionMode().trim().toUpperCase()));
        } else if (id == null && c.getExecutionMode() == null) {
            c.setExecutionMode(AgentExecutionMode.CLOUD_LLM);
        }
        if (body.sortOrder() != null) c.setSortOrder(body.sortOrder());
        if (body.enabled() != null) c.setEnabled(body.enabled());
        return careerView(careerRepository.save(c), industry != null ? industry : industryIndex().get(c.getIndustryId()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCatalog(boolean enabledOnly) {
        List<AgentCatalog> list = enabledOnly
                ? catalogRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
                : catalogRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, AgentCareer> careers = new LinkedHashMap<>();
        careerRepository.findAll().forEach(x -> careers.put(x.getId(), x));
        Map<Long, AgentIndustry> industries = industryIndex();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentCatalog cat : list) {
            AgentCareer career = careers.get(cat.getCareerId());
            AgentIndustry industry = career != null && career.getIndustryId() != null
                    ? industries.get(career.getIndustryId())
                    : null;
            out.add(catalogView(cat, career, industry));
        }
        Map<Long, List<Map<String, Object>>> kbByCatalog = officialKnowledgeService.briefsByCatalogIds(
                list.stream().map(AgentCatalog::getId).toList());
        for (Map<String, Object> row : out) {
            Long cid = (Long) row.get("id");
            row.put("knowledgeBases", kbByCatalog.getOrDefault(cid, List.of()));
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
        AgentIndustry industry = career.getIndustryId() != null
                ? industryRepository.findById(career.getIndustryId()).orElse(null)
                : null;
        Map<String, Object> view = catalogView(catalogRepository.save(c), career, industry);
        view.put("knowledgeBases", officialKnowledgeService.listCatalogKnowledge(c.getId()));
        return view;
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

    private Map<Long, AgentIndustry> industryIndex() {
        Map<Long, AgentIndustry> m = new LinkedHashMap<>();
        industryRepository.findAll().forEach(x -> m.put(x.getId(), x));
        return m;
    }

    private Map<String, Object> industryView(AgentIndustry c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("sortOrder", c.getSortOrder());
        m.put("enabled", c.isEnabled());
        return m;
    }

    private Map<String, Object> careerView(AgentCareer c, AgentIndustry industry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("industryId", c.getIndustryId());
        m.put("industryName", industry != null ? industry.getName() : null);
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("executionMode", c.getExecutionMode() != null ? c.getExecutionMode().name() : AgentExecutionMode.CLOUD_LLM.name());
        m.put("sortOrder", c.getSortOrder());
        m.put("enabled", c.isEnabled());
        return m;
    }

    private Map<String, Object> catalogView(AgentCatalog c, AgentCareer career, AgentIndustry industry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("careerId", c.getCareerId());
        m.put("careerName", career != null ? career.getName() : null);
        m.put("industryId", career != null ? career.getIndustryId() : null);
        m.put("industryName", industry != null ? industry.getName() : null);
        m.put("executionMode", career != null && career.getExecutionMode() != null
                ? career.getExecutionMode().name()
                : AgentExecutionMode.CLOUD_LLM.name());
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

    public record IndustryBody(String code, String name, String description, Integer sortOrder, Boolean enabled) {}

    public record CareerBody(
            Long industryId,
            Boolean clearIndustry,
            String code,
            String name,
            String description,
            String executionMode,
            Integer sortOrder,
            Boolean enabled
    ) {}

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
