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
 */
@Service
@RequiredArgsConstructor
public class BillingSettingsService {

    /** 加成倍率：在 API 成本之上乘的倍数，默认 4 */
    public static final String CFG_MARKUP_MULTIPLIER = "CHAT_BILLING_MARKUP_MULTIPLIER";
    /** 1 元人民币对应多少积分，默认 1000 */
    public static final String CFG_POINTS_PER_YUAN = "CHAT_POINTS_PER_YUAN";

    public static final double DEFAULT_MARKUP_MULTIPLIER = 4.0;
    public static final double DEFAULT_POINTS_PER_YUAN = 1000.0;

    private final SystemConfigRepository systemConfigRepository;

    public double markupMultiplier() {
        return readDouble(CFG_MARKUP_MULTIPLIER, DEFAULT_MARKUP_MULTIPLIER, 0.01, 1000);
    }

    public double pointsPerYuan() {
        return readDouble(CFG_POINTS_PER_YUAN, DEFAULT_POINTS_PER_YUAN, 1, 1_000_000);
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

    @Transactional(readOnly = true)
    public BillingSettingsView current() {
        return new BillingSettingsView(markupMultiplier(), pointsPerYuan());
    }

    @Transactional
    public BillingSettingsView update(Double markupMultiplier, Double pointsPerYuan) {
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

    public record BillingSettingsView(double markupMultiplier, double pointsPerYuan) {}
}
