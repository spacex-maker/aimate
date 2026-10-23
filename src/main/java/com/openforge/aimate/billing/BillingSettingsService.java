package com.openforge.aimate.billing;

import com.openforge.aimate.domain.SystemConfig;
import com.openforge.aimate.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat 计费全局规则：
 * <pre>
 * 用户扣积分 = ceil(三方 API 成本 CNY × 加成倍率 × 每元积分)
 * </pre>
 * 例：成本 1 元、倍率 4、1 元=1000 积分 → 扣 4000 积分。
 * <p>
 * 联网搜索（Tavily）按次计成本：basic / advanced 各有单价，再走同一套倍率与积分换算。
 * 参考 Tavily 公开价：basic≈1 credit、advanced≈2 credits（约 $0.008/credit）。
 */
@Service
@RequiredArgsConstructor
public class BillingSettingsService {

    /** 加成倍率：在 API 成本之上乘的倍数，默认 4 */
    public static final String CFG_MARKUP_MULTIPLIER = "CHAT_BILLING_MARKUP_MULTIPLIER";
    /** 1 元人民币对应多少积分，默认 1000 */
    public static final String CFG_POINTS_PER_YUAN = "CHAT_POINTS_PER_YUAN";
    /** 联网搜索 basic 单次系统成本（人民币），默认约合 Tavily 1 credit */
    public static final String CFG_WEB_SEARCH_COST_CNY_BASIC = "CHAT_WEB_SEARCH_COST_CNY_BASIC";
    /** 联网搜索 advanced 单次系统成本（人民币），默认约合 Tavily 2 credits */
    public static final String CFG_WEB_SEARCH_COST_CNY_ADVANCED = "CHAT_WEB_SEARCH_COST_CNY_ADVANCED";

    public static final double DEFAULT_MARKUP_MULTIPLIER = 4.0;
    public static final double DEFAULT_POINTS_PER_YUAN = 1000.0;
    /** ≈ $0.008 × 7.2 */
    public static final double DEFAULT_WEB_SEARCH_COST_CNY_BASIC = 0.06;
    public static final double DEFAULT_WEB_SEARCH_COST_CNY_ADVANCED = 0.12;

    private final SystemConfigRepository systemConfigRepository;

    public double markupMultiplier() {
        return readDouble(CFG_MARKUP_MULTIPLIER, DEFAULT_MARKUP_MULTIPLIER, 0.01, 1000);
    }

    public double pointsPerYuan() {
        return readDouble(CFG_POINTS_PER_YUAN, DEFAULT_POINTS_PER_YUAN, 1, 1_000_000);
    }

    public double webSearchCostCnyBasic() {
        return readDouble(CFG_WEB_SEARCH_COST_CNY_BASIC, DEFAULT_WEB_SEARCH_COST_CNY_BASIC, 0, 100);
    }

    public double webSearchCostCnyAdvanced() {
        return readDouble(CFG_WEB_SEARCH_COST_CNY_ADVANCED, DEFAULT_WEB_SEARCH_COST_CNY_ADVANCED, 0, 100);
    }

    /**
     * 按 Tavily search_depth 取单次系统成本 CNY。
     * advanced / 其它非 basic → advanced 单价；basic / fast / ultra-fast → basic 单价。
     */
    public double webSearchCostCny(String searchDepth) {
        String d = searchDepth == null ? "" : searchDepth.trim().toLowerCase();
        if ("advanced".equals(d)) {
            return webSearchCostCnyAdvanced();
        }
        return webSearchCostCnyBasic();
    }

    /**
     * 由 API 成本 CNY 计算应扣积分（向上取整，至少为 0）。
     */
    public long pointsForApiCostCny(double costCny) {
        if (costCny <= 0 || Double.isNaN(costCny) || Double.isInfinite(costCny)) {
            return 0L;
        }
        double raw = costCny * markupMultiplier() * pointsPerYuan();
        if (raw <= 0 || Double.isNaN(raw) || Double.isInfinite(raw)) {
            return 0L;
        }
        long points = (long) Math.ceil(raw);
        return Math.max(0L, points);
    }

    public long pointsForWebSearch(String searchDepth) {
        return pointsForApiCostCny(webSearchCostCny(searchDepth));
    }

    @Transactional(readOnly = true)
    public BillingSettingsView current() {
        return new BillingSettingsView(
                markupMultiplier(),
                pointsPerYuan(),
                webSearchCostCnyBasic(),
                webSearchCostCnyAdvanced()
        );
    }

    @Transactional
    public BillingSettingsView update(Double markupMultiplier, Double pointsPerYuan,
                                      Double webSearchCostCnyBasic, Double webSearchCostCnyAdvanced) {
        if (markupMultiplier != null) {
            double v = clamp(markupMultiplier, 0.01, 1000);
            upsert(CFG_MARKUP_MULTIPLIER, formatDouble(v),
                    "计费加成倍率。用户扣积分 = ceil(API成本CNY × 倍率 × 每元积分)。默认 4。");
        }
        if (pointsPerYuan != null) {
            double v = clamp(pointsPerYuan, 1, 1_000_000);
            upsert(CFG_POINTS_PER_YUAN, formatDouble(v),
                    "积分与人民币比例：1 元 = N 积分。默认 1000。");
        }
        if (webSearchCostCnyBasic != null) {
            double v = clamp(webSearchCostCnyBasic, 0, 100);
            upsert(CFG_WEB_SEARCH_COST_CNY_BASIC, formatDouble(v),
                    "联网搜索（Tavily basic）单次系统成本 CNY。默认 0.06。");
        }
        if (webSearchCostCnyAdvanced != null) {
            double v = clamp(webSearchCostCnyAdvanced, 0, 100);
            upsert(CFG_WEB_SEARCH_COST_CNY_ADVANCED, formatDouble(v),
                    "联网搜索（Tavily advanced）单次系统成本 CNY。默认 0.12。");
        }
        return current();
    }

    private double readDouble(String key, double defaultVal, double min, double max) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> {
                    try {
                        return clamp(Double.parseDouble(s.trim()), min, max);
                    } catch (Exception e) {
                        return defaultVal;
                    }
                })
                .orElse(defaultVal);
    }

    private void upsert(String key, String value, String description) {
        SystemConfig row = systemConfigRepository.findByConfigKey(key).orElseGet(SystemConfig::new);
        row.setConfigKey(key);
        row.setConfigValue(value);
        row.setDescription(description);
        systemConfigRepository.save(row);
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return min;
        return Math.min(max, Math.max(min, v));
    }

    private static String formatDouble(double v) {
        if (Math.rint(v) == v) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    public record BillingSettingsView(
            double markupMultiplier,
            double pointsPerYuan,
            double webSearchCostCnyBasic,
            double webSearchCostCnyAdvanced
    ) {}
}
