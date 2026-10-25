package com.openforge.aimate.knowledge;

import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentCatalogKnowledge;
import com.openforge.aimate.domain.KbCategory;
import com.openforge.aimate.domain.KnowledgeBase;
import com.openforge.aimate.repository.AgentCatalogKnowledgeRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.KbCategoryRepository;
import com.openforge.aimate.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 官方知识库种子：行业分层分类 + 原子知识库正文（可对过短旧数据 enrich），并挂到招聘商品。
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class OfficialKnowledgeBootstrap implements ApplicationRunner {

    private final KbCategoryRepository categoryRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentCatalogRepository catalogRepository;
    private final AgentCareerRepository careerRepository;
    private final AgentCatalogKnowledgeRepository catalogKnowledgeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[OfficialKB] ensuring industry knowledge tree…");

        KbCategory tech = cat(null, "TECH", "技术研发", "软件工程、后端、前端与基础设施", 10);
        KbCategory techBe = cat(tech.getId(), "TECH_BACKEND", "后端工程", "服务端、API、数据存储", 10);
        KbCategory techFe = cat(tech.getId(), "TECH_FRONTEND", "前端工程", "Web/App 界面与交互", 20);
        KbCategory techInfra = cat(tech.getId(), "TECH_INFRA", "基础设施", "云原生、运维与稳定性", 30);

        KbCategory product = cat(null, "PRODUCT", "产品运营", "产品规划、运营增长与项目管理", 20);
        KbCategory productPm = cat(product.getId(), "PRODUCT_PM", "产品管理", "需求、规划与路标", 10);
        KbCategory productOps = cat(product.getId(), "PRODUCT_OPS", "用户运营", "增长、留存与活动", 20);

        KbCategory design = cat(null, "DESIGN", "设计创意", "UI/UX、视觉与品牌体验", 30);
        KbCategory designUi = cat(design.getId(), "DESIGN_UI", "界面设计", "组件、规范与可用性", 10);
        KbCategory designBrand = cat(design.getId(), "DESIGN_BRAND", "品牌视觉", "识别系统与物料", 20);

        KbCategory data = cat(null, "DATA", "数据智能", "数据分析、商业智能与机器学习", 40);
        KbCategory dataBi = cat(data.getId(), "DATA_BI", "商业分析", "指标体系与报表洞察", 10);
        KbCategory dataMl = cat(data.getId(), "DATA_ML", "机器学习", "特征、建模与评估", 20);

        KbCategory cb = cat(null, "CROSS_BORDER", "跨境电商", "跨境平台运营、独立站、选品供应链与投放", 50);
        KbCategory cbOps = cat(cb.getId(), "CB_OPS", "平台运营", "多平台店铺与 Listing", 10);
        KbCategory cbAmazon = cat(cbOps.getId(), "CB_AMAZON", "亚马逊", "Amazon 运营与广告", 10);
        KbCategory cbTiktok = cat(cbOps.getId(), "CB_TIKTOK", "TikTok Shop", "短视频带货与直播", 20);
        KbCategory cbSupply = cat(cb.getId(), "CB_SUPPLY", "供应链", "选品、采购与履约", 20);
        KbCategory cbAds = cat(cb.getId(), "CB_ADS", "投放增长", "广告与 ROI", 30);

        KbCategory ai = cat(null, "AI", "人工智能", "本机 AI 编程助手与 Agent 协作", 5);
        KbCategory aiIde = cat(ai.getId(), "AI_IDE", "IDE Agent", "Cursor / Codex 等本地桥接", 10);

        KbCategory marketing = cat(null, "MARKETING", "市场营销", "品牌、内容与渠道获客", 60);
        KbCategory mktContent = cat(marketing.getId(), "MKT_CONTENT", "内容营销", "内容策略与分发", 10);
        KbCategory mktChannel = cat(marketing.getId(), "MKT_CHANNEL", "渠道获客", "投放与合作", 20);

        KbCategory legal = cat(null, "LEGAL", "法律合规", "合同、知产与平台合规", 70);
        KbCategory legalContract = cat(legal.getId(), "LEGAL_CONTRACT", "合同审查", "条款与风险清单", 10);

        KbCategory finance = cat(null, "FINANCE", "财务会计", "核算、报表与税务基础", 80);
        KbCategory finBook = cat(finance.getId(), "FIN_BOOKKEEPING", "会计核算", "科目与凭证规范", 10);

        KbCategory investment = cat(null, "INVESTMENT", "金融投资", "风险投资、私募股权与投融资", 55);
        KbCategory invVc = cat(investment.getId(), "INV_VC", "风险投资", "尽调、条款与投后", 10);

        AtomicInteger enriched = new AtomicInteger();
        AtomicInteger created = new AtomicInteger();

        KnowledgeBase javaApi = kb(techBe.getId(), "KB_JAVA_API", "Java 服务端 API 设计规范",
                "REST 命名、错误码、分页、幂等与兼容性约定",
                "java,api,rest", 10, enriched, created);
        KnowledgeBase feReact = kb(techFe.getId(), "KB_FE_REACT", "前端 React 工程约定",
                "组件分层、服务端分页、统一 Select 与状态边界",
                "react,frontend", 10, enriched, created);
        KnowledgeBase sre = kb(techInfra.getId(), "KB_SRE_ONCALL", "线上稳定性与值班手册",
                "告警分级、止损回滚、沟通与复盘模板",
                "sre,ops", 10, enriched, created);

        KnowledgeBase prd = kb(productPm.getId(), "KB_PRD_TEMPLATE", "PRD 写作模板",
                "背景、目标/非目标、方案、指标、验收与风险",
                "prd,product", 10, enriched, created);
        KnowledgeBase growth = kb(productOps.getId(), "KB_GROWTH_LOOP", "增长飞轮与留存",
                "获客-激活-留存-变现闭环与实验纪律",
                "growth,ops", 10, enriched, created);

        KnowledgeBase uiSys = kb(designUi.getId(), "KB_UI_SYSTEM", "设计系统与组件规范",
                "间距、字号、颜色语义与组件全状态",
                "ui,design-system", 10, enriched, created);
        KnowledgeBase brand = kb(designBrand.getId(), "KB_BRAND_VOICE", "品牌语气与视觉基线",
                "品牌关键词、禁用表达与物料安全区",
                "brand,visual", 10, enriched, created);

        KnowledgeBase metric = kb(dataBi.getId(), "KB_METRIC_DICT", "业务指标字典",
                "DAU/留存/转化口径与版本化变更",
                "metrics,bi", 10, enriched, created);
        KnowledgeBase mlEval = kb(dataMl.getId(), "KB_ML_EVAL", "模型评估清单",
                "离线指标、关键切片与上线回滚门槛",
                "ml,evaluation", 10, enriched, created);

        KnowledgeBase listing = kb(cbOps.getId(), "KB_CB_LISTING", "跨境 Listing 优化要点",
                "标题、五点、主图、A+ 与关键词迭代",
                "listing,cross-border", 10, enriched, created);
        KnowledgeBase amazon = kb(cbAmazon.getId(), "KB_AMAZON_ADS", "亚马逊广告入门",
                "自动/手动广告结构、收词与 ACOS 目标",
                "amazon,ads", 10, enriched, created);
        KnowledgeBase tiktok = kb(cbTiktok.getId(), "KB_TIKTOK_LIVE", "TikTok Shop 内容与直播",
                "短视频钩子、直播节奏与合规话术",
                "tiktok,live", 10, enriched, created);
        KnowledgeBase supply = kb(cbSupply.getId(), "KB_CB_SUPPLY", "跨境供应链协同清单",
                "质检、头程、海外仓与滞销决策",
                "supply-chain", 10, enriched, created);
        KnowledgeBase ads = kb(cbAds.getId(), "KB_CB_ADS_ROI", "跨境投放 ROI 框架",
                "预算分配、归因窗口与渠道止损",
                "ads,roi", 10, enriched, created);

        KnowledgeBase localAi = kb(aiIde.getId(), "KB_LOCAL_BRIDGE", "Local Bridge 协作规范",
                "群聊 @ 派发、claim/complete、worker 保活与安全",
                "local-bridge,cursor,codex", 10, enriched, created);

        KnowledgeBase content = kb(mktContent.getId(), "KB_CONTENT_CALENDAR", "内容日历与选题",
                "栏目节奏、选题来源、形态复用与复盘",
                "content,marketing", 10, enriched, created);
        KnowledgeBase channel = kb(mktChannel.getId(), "KB_CHANNEL_MIX", "渠道组合策略",
                "付费/自然/合作权重与 CAC-LTV 复盘",
                "channel,cac", 10, enriched, created);

        KnowledgeBase contract = kb(legalContract.getId(), "KB_CONTRACT_CHECK", "合同审查清单",
                "主体、标的、付款、违约、IP 与管辖",
                "legal,contract", 10, enriched, created);

        KnowledgeBase book = kb(finBook.getId(), "KB_BOOKKEEPING_BASIC", "会计核算基础",
                "借贷、权责发生制与报销纪律",
                "finance,bookkeeping", 10, enriched, created);

        KnowledgeBase vcDd = kb(invVc.getId(), "KB_VC_DUE_DILIGENCE", "创业项目尽调清单",
                "团队、市场、产品、财务、法务与风险红旗",
                "vc,due-diligence", 10, enriched, created);
        KnowledgeBase vcTerm = kb(invVc.getId(), "KB_VC_TERM_SHEET", "投资条款要点",
                "估值、股权、清算优先、反稀释与董事会席位",
                "vc,term-sheet", 20, enriched, created);

        linkCareer("JAVA_ENGINEER", javaApi, sre);
        linkCareer("PRODUCT_MANAGER", prd, growth);
        linkCareer("UI_DESIGNER", uiSys, brand);
        linkCareer("DATA_ANALYST", metric, mlEval);
        linkCareer("CB_OPS", listing, ads);
        linkCareer("AMAZON_OPS", amazon, listing);
        linkCareer("TIKTOK_SHOP_OPS", tiktok, listing);
        linkCareer("CB_SUPPLY_CHAIN", supply);
        linkCareer("CB_SOURCING", supply, listing);
        linkCareer("CB_ADS", ads);
        linkCareer("CURSOR", localAi);
        linkCareer("CODEX", localAi);
        linkCareer("CLAUDE_CODE", localAi);
        linkCareer("GENERIC_LOCAL_AI", localAi);
        linkCareer("VC_EXPERT", vcDd, vcTerm);

        // 避免未使用告警（content/channel/contract/book/feReact 已入库，关联按职业）
        log.info(
                "[OfficialKB] done categories={} knowledgeBases={} created={} enriched={} (also seeded: {},{},{},{},{})",
                categoryRepository.count(),
                knowledgeBaseRepository.count(),
                created.get(),
                enriched.get(),
                feReact.getCode(),
                content.getCode(),
                channel.getCode(),
                contract.getCode(),
                book.getCode()
        );
    }

    private KbCategory cat(Long parentId, String code, String name, String desc, int sort) {
        return categoryRepository.findByCode(code).orElseGet(() -> {
            KbCategory c = new KbCategory();
            c.setParentId(parentId);
            c.setCode(code);
            c.setName(name);
            c.setDescription(desc);
            c.setSortOrder(sort);
            c.setEnabled(true);
            return categoryRepository.save(c);
        });
    }

    private KnowledgeBase kb(
            Long categoryId,
            String code,
            String name,
            String summary,
            String tags,
            int sort,
            AtomicInteger enriched,
            AtomicInteger created
    ) {
        String content = OfficialKnowledgeDocs.load(code);
        return knowledgeBaseRepository.findByCode(code).map(existing -> {
            if (OfficialKnowledgeDocs.shouldEnrich(existing.getContent(), content)) {
                existing.setName(name);
                existing.setSummary(summary);
                existing.setContent(content);
                existing.setTags(tags);
                if (existing.getCategoryId() == null) existing.setCategoryId(categoryId);
                enriched.incrementAndGet();
                return knowledgeBaseRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            KnowledgeBase k = new KnowledgeBase();
            k.setPublicId("kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            k.setCategoryId(categoryId);
            k.setCode(code);
            k.setName(name);
            k.setSummary(summary);
            k.setContent(content);
            k.setTags(tags);
            k.setSortOrder(sort);
            k.setEnabled(true);
            created.incrementAndGet();
            return knowledgeBaseRepository.save(k);
        });
    }

    private void linkCareer(String careerCode, KnowledgeBase... kbs) {
        var career = careerRepository.findByCodeIgnoreCase(careerCode).orElse(null);
        if (career == null) return;
        List<AgentCatalog> catalogs = catalogRepository.findByCareerIdOrderBySortOrderAscIdAsc(career.getId());
        for (AgentCatalog cat : catalogs) {
            for (KnowledgeBase kb : kbs) {
                boolean exists = catalogKnowledgeRepository.findByCatalogIdOrderByIdAsc(cat.getId()).stream()
                        .anyMatch(l -> l.getKnowledgeBaseId().equals(kb.getId()));
                if (exists) continue;
                AgentCatalogKnowledge link = new AgentCatalogKnowledge();
                link.setCatalogId(cat.getId());
                link.setKnowledgeBaseId(kb.getId());
                catalogKnowledgeRepository.save(link);
            }
        }
    }
}
