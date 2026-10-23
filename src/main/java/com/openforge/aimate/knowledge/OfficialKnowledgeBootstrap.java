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

/**
 * 官方知识库种子：常见行业分层分类 + 原子知识库，并尽量挂到已有官方招聘商品。
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
        if (categoryRepository.count() > 0) {
            log.info("[OfficialKB] categories already present, skip seed");
            return;
        }
        log.info("[OfficialKB] seeding industry knowledge tree…");

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

        // 原子知识库
        KnowledgeBase javaApi = kb(techBe.getId(), "KB_JAVA_API", "Java 服务端 API 设计规范",
                "REST 命名、错误码、分页与幂等约定",
                """
                        # Java API 设计要点
                        1. 资源名词复数路径；动作用 HTTP 动词表达。
                        2. 列表默认 page/size，响应含 items/page/size/total/totalPages。
                        3. 写操作提供幂等键；失败返回可机读 errorCode。
                        4. 认证走 Bearer；禁止在日志打印密钥。
                        5. 变更向后兼容：新增字段可选，废弃字段保留至少一个版本。
                        """,
                "java,api,rest", 10);
        KnowledgeBase feReact = kb(techFe.getId(), "KB_FE_REACT", "前端 React 工程约定",
                "组件分层、状态与请求边界",
                """
                        # React 工程约定
                        1. 页面容器负责数据，展示组件保持纯 UI。
                        2. 服务端分页，禁止一次拉全表再切页。
                        3. 下拉统一用项目 Select，禁止原生 select。
                        4. 错误用 toast 人类可读文案；加载与空态分开。
                        """,
                "react,frontend", 10);
        KnowledgeBase sre = kb(techInfra.getId(), "KB_SRE_ONCALL", "线上稳定性与值班手册",
                "告警分级、回滚与复盘模板",
                """
                        # 值班速查
                        P0：全站不可用 → 立即回滚 + 拉通；P1：核心链路降级；P2：非核心。
                        变更必须可回滚；复盘写清时间线、根因、改进项负责人与截止日期。
                        """,
                "sre,ops", 10);

        KnowledgeBase prd = kb(productPm.getId(), "KB_PRD_TEMPLATE", "PRD 写作模板",
                "背景、目标、方案、指标与边界",
                """
                        # PRD 结构
                        背景与问题 → 目标/非目标 → 用户故事 → 方案与交互 → 数据指标 → 风险与排期。
                        每个需求写清验收标准；避免无度量的「提升体验」空话。
                        """,
                "prd,product", 10);
        KnowledgeBase growth = kb(productOps.getId(), "KB_GROWTH_LOOP", "增长飞轮与留存",
                "获客-激活-留存-变现闭环",
                """
                        # 增长要点
                        先定义北极星指标，再拆激活与留存漏斗。
                        活动有对照实验；渠道 ROI 周复盘；沉默用户唤醒要可关停。
                        """,
                "growth,ops", 10);

        KnowledgeBase uiSys = kb(designUi.getId(), "KB_UI_SYSTEM", "设计系统与组件规范",
                "间距、字号、颜色与组件状态",
                """
                        # UI 系统
                        8pt 网格；主文/次文/弱文三级对比。
                        组件覆盖 default/hover/disabled/error；表单错误贴近字段。
                        无障碍：对比度、焦点环、可读替代文本。
                        """,
                "ui,design-system", 10);
        KnowledgeBase brand = kb(designBrand.getId(), "KB_BRAND_VOICE", "品牌语气与视觉基线",
                "品牌关键词、禁用表达与物料清单",
                """
                        # 品牌基线
                        语气：专业、克制、可信；避免夸张承诺与黑话堆砌。
                        Logo 安全区与最小尺寸必须遵守；对外物料统一主色与字体。
                        """,
                "brand,visual", 10);

        KnowledgeBase metric = kb(dataBi.getId(), "KB_METRIC_DICT", "业务指标字典",
                "DAU/留存/转化口径与注意事项",
                """
                        # 指标口径
                        DAU：自然日内至少一次有效会话的独立用户。
                        次日留存：D0 新用户在 D1 仍活跃的比例。
                        转化漏斗步骤定义需产品/数据双方签字，变更要版本号。
                        """,
                "metrics,bi", 10);
        KnowledgeBase mlEval = kb(dataMl.getId(), "KB_ML_EVAL", "模型评估清单",
                "离线指标、切片与上线门槛",
                """
                        # 评估
                        分类看 Precision/Recall/F1；排序看 AUC/NDCG。
                        必做关键切片（新老客、地域）；上线设回滚阈值与影子流量。
                        """,
                "ml,evaluation", 10);

        KnowledgeBase listing = kb(cbOps.getId(), "KB_CB_LISTING", "跨境 Listing 优化要点",
                "标题、五点、主图与 A+ 基础",
                """
                        # Listing
                        标题含核心关键词但不堆砌；五点说清卖点与差异。
                        主图白底合规；A+/视频提升停留。每周看搜索词报告迭代。
                        """,
                "listing,cross-border", 10);
        KnowledgeBase amazon = kb(cbAmazon.getId(), "KB_AMAZON_ADS", "亚马逊广告入门",
                "自动/手动广告与 ACOS 目标",
                """
                        # Amazon Ads
                        新品先自动广告收词，再转手动精确/词组。
                        设定目标 ACOS；否定词周清理；库存紧张时降预算防断货差评。
                        """,
                "amazon,ads", 10);
        KnowledgeBase tiktok = kb(cbTiktok.getId(), "KB_TIKTOK_LIVE", "TikTok Shop 内容与直播",
                "短视频钩子、直播节奏与转化",
                """
                        # TikTok Shop
                        前 3 秒给钩子；人设稳定；SKU 少而精。
                        直播：引流→演示→限时→逼单；话术合规，禁用绝对化承诺。
                        """,
                "tiktok,live", 10);
        KnowledgeBase supply = kb(cbSupply.getId(), "KB_CB_SUPPLY", "跨境供应链协同清单",
                "质检、头程、海外仓节点",
                """
                        # 供应链
                        下单前确认 MOQ/交期/质检标准；头程时效与旺季加价提前规划。
                        海外仓备货按周转天数；滞销触发清仓策略。
                        """,
                "supply-chain", 10);
        KnowledgeBase ads = kb(cbAds.getId(), "KB_CB_ADS_ROI", "跨境投放 ROI 框架",
                "预算分配与归因注意",
                """
                        # 投放 ROI
                        渠道预算按边际 ROI 分配；归因窗口与平台报表对齐。
                        创意疲劳要轮换；亏损渠道设止损线。
                        """,
                "ads,roi", 10);

        KnowledgeBase localAi = kb(aiIde.getId(), "KB_LOCAL_BRIDGE", "Local Bridge 协作规范",
                "群聊 @ 派发、claim/complete 与上下文",
                """
                        # Local Bridge
                        仅在被 @ / 回复时领取任务；结合人设与近期对话完成。
                        claim → 拉 context → 执行 → complete 回帖；失败也要回报错误摘要。
                        不把密钥写入对话或仓库。
                        """,
                "local-bridge,cursor", 10);

        KnowledgeBase content = kb(mktContent.getId(), "KB_CONTENT_CALENDAR", "内容日历与选题",
                "栏目节奏与复用",
                """
                        # 内容
                        栏目固定节奏（周更/双周）；选题对齐用户痛点与产品卖点。
                        长文可拆短视频/社群金句；发布后看完读与转化，淘汰低效选题。
                        """,
                "content,marketing", 10);
        KnowledgeBase channel = kb(mktChannel.getId(), "KB_CHANNEL_MIX", "渠道组合策略",
                "付费/自然/合作权重",
                """
                        # 渠道
                        付费获客补量，自然流量建护城河，合作拓展信任。
                        统一 UTM；周会看 CAC 与 LTV 比，而不是只看曝光。
                        """,
                "channel,cac", 10);

        KnowledgeBase contract = kb(legalContract.getId(), "KB_CONTRACT_CHECK", "合同审查清单",
                "标的、付款、违约与管辖",
                """
                        # 合同审查
                        核对主体、标的、交付、验收、付款、违约、保密、知识产权与争议解决。
                        无限责任/单方随意解约需标红；涉外合同注意适用法律与语言版本。
                        """,
                "legal,contract", 10);

        KnowledgeBase book = kb(finBook.getId(), "KB_BOOKKEEPING_BASIC", "会计核算基础",
                "借贷与常见科目",
                """
                        # 会计基础
                        资产=负债+所有者权益；费用增加借方，收入增加贷方。
                        费用报销需票据齐全；跨期费用按权责发生制摊销。
                        """,
                "finance,bookkeeping", 10);

        // 关联到已有招聘目录（按职业 code）
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

        log.info("[OfficialKB] seeded categories={} knowledgeBases={}",
                categoryRepository.count(), knowledgeBaseRepository.count());
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

    private KnowledgeBase kb(Long categoryId, String code, String name, String summary, String content, String tags, int sort) {
        return knowledgeBaseRepository.findByCode(code).orElseGet(() -> {
            KnowledgeBase k = new KnowledgeBase();
            k.setPublicId("kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            k.setCategoryId(categoryId);
            k.setCode(code);
            k.setName(name);
            k.setSummary(summary);
            k.setContent(content.stripIndent().trim());
            k.setTags(tags);
            k.setSortOrder(sort);
            k.setEnabled(true);
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
