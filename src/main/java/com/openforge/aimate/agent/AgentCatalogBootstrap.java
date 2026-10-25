package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentCareer;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentExecutionMode;
import com.openforge.aimate.domain.AgentIndustry;
import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentIndustryRepository;
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

    private final AgentIndustryRepository industryRepository;
    private final AgentCareerRepository careerRepository;
    private final AgentCatalogRepository catalogRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIndustry("TECH", "技术研发", "软件工程、后端、前端与基础设施", 10);
        seedIndustry("PRODUCT", "产品运营", "产品规划、运营增长与项目管理", 20);
        seedIndustry("DESIGN", "设计创意", "UI/UX、视觉与品牌体验", 30);
        seedIndustry("DATA", "数据智能", "数据分析、商业智能与机器学习", 40);
        seedIndustry("CROSS_BORDER", "跨境电商", "跨境平台运营、独立站、选品供应链与投放增长", 50);
        seedIndustry("INVESTMENT", "金融投资", "风险投资、私募股权、投融资顾问与投后管理", 55);
        seedIndustry("AI", "人工智能", "本机 Cursor / Codex 等 AI 编程助手，经 Local Bridge 拉群聊任务执行", 5);

        seedCareer("JAVA_ENGINEER", "Java 开发工程师", "后端与服务端开发", "TECH", 20, false);
        seedCareer("PRODUCT_MANAGER", "产品经理", "需求与产品规划", "PRODUCT", 30, false);
        seedCareer("UI_DESIGNER", "UI 设计师", "界面与体验设计", "DESIGN", 10, false);
        seedCareer("DATA_ANALYST", "数据分析师", "数据洞察与报表", "DATA", 40, false);
        seedCareer("VC_EXPERT", "风投专家", "项目尽调、估值条款、投决材料与投后赋能", "INVESTMENT", 10, false);

        seedCareer("CB_OPS", "跨境运营专员", "多平台店铺日常运营、Listing 与转化优化", "CROSS_BORDER", 10, false);
        seedCareer("AMAZON_OPS", "亚马逊运营", "Amazon 店铺、广告、库存与品牌旗舰", "CROSS_BORDER", 20, false);
        seedCareer("INDEPENDENT_SITE_OPS", "独立站运营", "Shopify/自建站获客、转化与复购", "CROSS_BORDER", 30, false);
        seedCareer("TIKTOK_SHOP_OPS", "TikTok Shop 运营", "短视频带货、直播与店铺增长", "CROSS_BORDER", 40, false);
        seedCareer("CB_SOURCING", "跨境选品专员", "市场调研、竞品分析与爆品选品", "CROSS_BORDER", 50, false);
        seedCareer("CB_SUPPLY_CHAIN", "跨境供应链专员", "采购、质检、头程物流与海外仓协同", "CROSS_BORDER", 60, false);
        seedCareer("CB_CUSTOMER_SERVICE", "跨境客服", "多语言售前售后、纠纷与评价维护", "CROSS_BORDER", 70, false);
        seedCareer("CB_ADS", "跨境广告投放", "Google/Meta/平台广告投放与 ROI 优化", "CROSS_BORDER", 80, false);

        seedCareer("CURSOR", "Cursor", "本机 Cursor Agent，群聊 @ 后领取任务改代码/跑命令", "AI", 10, true);
        seedCareer("CODEX", "Codex", "本机 OpenAI Codex / ChatGPT 远程控，领取群聊任务执行", "AI", 20, true);
        seedCareer("WORKBUDDY", "Workbuddy", "本机 Workbuddy 桌面助手，可视驱动聊天框执行群聊任务", "AI", 25, true);
        seedCareer("CLAUDE_CODE", "Claude Code", "本机 Claude Code CLI，领取群聊任务执行", "AI", 30, true);
        seedCareer("WINDSURF", "Windsurf", "本机 Windsurf / Cascade，领取群聊任务执行", "AI", 40, true);
        seedCareer("GITHUB_COPILOT", "GitHub Copilot", "本机 Copilot / Copilot CLI 工作流", "AI", 50, true);
        seedCareer("AIDER", "Aider", "本机 Aider 结对编程助手", "AI", 60, true);
        seedCareer("GENERIC_LOCAL_AI", "通用本地 AI", "任意本机 Agent（自建 worker / SDK）", "AI", 90, true);

        for (AgentCareer career : careerRepository.findAll()) {
            for (AgentTier tier : AgentTier.values()) {
                if (catalogRepository.findByCareerIdAndTier(career.getId(), tier).isPresent()) continue;
                AgentCatalog c = new AgentCatalog();
                c.setCareerId(career.getId());
                c.setTier(tier);
                c.setName(career.getName() + " · " + tierLabel(tier));
                c.setDescription(career.getDescription() + "（" + tierLabel(tier) + "）");
                if (career.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE) {
                    c.setPersonaTemplate(
                            "你是本机「" + career.getName() + "」执行通道（" + tierLabel(tier) + "）。"
                                    + "群聊中仅在被 @ 或被回复时领取任务；结合群上下文与专属 Soul 完成工作，"
                                    + "结果回帖到群。不要假装云端即时聊天。"
                    );
                } else {
                    c.setPersonaTemplate("你是一名" + tierLabel(tier) + career.getName() + "，专业、可靠、沟通清晰。");
                }
                c.setDefaultGender("UNSPECIFIED");
                applyDefaultPrices(c, tier);
                c.setEnabled(true);
                c.setSortOrder(career.getSortOrder() * 10 + tier.ordinal());
                catalogRepository.save(c);
            }
        }
    }

    private void seedIndustry(String code, String name, String desc, int sort) {
        industryRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
            AgentIndustry i = new AgentIndustry();
            i.setCode(code);
            i.setName(name);
            i.setDescription(desc);
            i.setSortOrder(sort);
            i.setEnabled(true);
            return industryRepository.save(i);
        });
    }

    private void seedCareer(
            String code,
            String name,
            String desc,
            String industryCode,
            int sort,
            boolean localBridge
    ) {
        Long industryId = industryRepository.findByCodeIgnoreCase(industryCode)
                .map(AgentIndustry::getId)
                .orElse(null);
        AgentExecutionMode mode = localBridge ? AgentExecutionMode.LOCAL_BRIDGE : AgentExecutionMode.CLOUD_LLM;
        careerRepository.findByCodeIgnoreCase(code).ifPresentOrElse(existing -> {
            boolean dirty = false;
            if (existing.getIndustryId() == null && industryId != null) {
                existing.setIndustryId(industryId);
                dirty = true;
            }
            if (localBridge && existing.getExecutionMode() != AgentExecutionMode.LOCAL_BRIDGE) {
                existing.setExecutionMode(AgentExecutionMode.LOCAL_BRIDGE);
                dirty = true;
            }
            if (dirty) careerRepository.save(existing);
        }, () -> {
            AgentCareer c = new AgentCareer();
            c.setCode(code);
            c.setName(name);
            c.setDescription(desc);
            c.setIndustryId(industryId);
            c.setExecutionMode(mode);
            c.setSortOrder(sort);
            c.setEnabled(true);
            careerRepository.save(c);
        });
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
