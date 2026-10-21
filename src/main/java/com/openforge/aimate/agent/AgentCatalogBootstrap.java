package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentCareer;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(40)
@RequiredArgsConstructor
public class AgentCatalogBootstrap implements ApplicationRunner {

    private final AgentCareerRepository careerRepository;
    private final AgentCatalogRepository catalogRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCareer("UI_DESIGNER", "UI 设计师", "界面与体验设计", 10);
        seedCareer("JAVA_ENGINEER", "Java 开发工程师", "后端与服务端开发", 20);
        seedCareer("PRODUCT_MANAGER", "产品经理", "需求与产品规划", 30);
        seedCareer("DATA_ANALYST", "数据分析师", "数据洞察与报表", 40);

        for (AgentCareer career : careerRepository.findAll()) {
            for (AgentTier tier : AgentTier.values()) {
                if (catalogRepository.findByCareerIdAndTier(career.getId(), tier).isPresent()) continue;
                AgentCatalog c = new AgentCatalog();
                c.setCareerId(career.getId());
                c.setTier(tier);
                c.setName(career.getName() + " · " + tierLabel(tier));
                c.setDescription(career.getDescription() + "（" + tierLabel(tier) + "）");
                c.setPersonaTemplate("你是一名" + tierLabel(tier) + career.getName() + "，专业、可靠、沟通清晰。");
                c.setDefaultGender("UNSPECIFIED");
                applyDefaultPrices(c, tier);
                c.setEnabled(true);
                c.setSortOrder(career.getSortOrder() * 10 + tier.ordinal());
                catalogRepository.save(c);
            }
        }
    }

    private void seedCareer(String code, String name, String desc, int sort) {
        if (careerRepository.findByCodeIgnoreCase(code).isPresent()) return;
        AgentCareer c = new AgentCareer();
        c.setCode(code);
        c.setName(name);
        c.setDescription(desc);
        c.setSortOrder(sort);
        c.setEnabled(true);
        careerRepository.save(c);
    }

    private static void applyDefaultPrices(AgentCatalog c, AgentTier tier) {
        long mult = switch (tier) {
            case JUNIOR -> 1;
            case MID -> 2;
            case SENIOR -> 4;
            case EXPERT -> 8;
        };
        c.setPriceDayPoints(100 * mult);
        c.setPriceMonthPoints(2000 * mult);
        c.setPriceYearPoints(18000 * mult);
        c.setSoulPriceDayPoints(50 * mult);
        c.setSoulPriceMonthPoints(800 * mult);
        c.setSoulPriceYearPoints(7000 * mult);
    }

    private static String tierLabel(AgentTier t) {
        return switch (t) {
            case JUNIOR -> "初级";
            case MID -> "中级";
            case SENIOR -> "高级";
            case EXPERT -> "专家";
        };
    }
}
