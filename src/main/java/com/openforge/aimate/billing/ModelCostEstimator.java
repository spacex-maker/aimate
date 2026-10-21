package com.openforge.aimate.billing;

import com.openforge.aimate.domain.SystemModel;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 按北京时间高峰/非高峰估算系统成本 CNY。
 * 高峰：周一至周五 9–12、14–18（与 DeepSeek 官方一致）。
 * 无模型单价时使用 Flash 默认官价（缓存未命中口径）。
 */
public final class ModelCostEstimator {

    private static final ZoneId BJ = ZoneId.of("Asia/Shanghai");

    private ModelCostEstimator() {}

    public static boolean isPeak(LocalDateTime whenBj) {
        DayOfWeek d = whenBj.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return false;
        int h = whenBj.getHour();
        return (h >= 9 && h < 12) || (h >= 14 && h < 18);
    }

    public static double estimateCny(SystemModel model, int promptTokens, int completionTokens) {
        LocalDateTime now = LocalDateTime.now(BJ);
        boolean peak = isPeak(now);
        double inPerM = pick(
                model,
                peak,
                model != null ? model.getCostInputCnyPer1mPeak() : null,
                model != null ? model.getCostInputCnyPer1mOffpeak() : null,
                3.0, 1.5);
        double outPerM = pick(
                model,
                peak,
                model != null ? model.getCostOutputCnyPer1mPeak() : null,
                model != null ? model.getCostOutputCnyPer1mOffpeak() : null,
                9.0, 4.5);
        return (Math.max(0, promptTokens) / 1_000_000.0) * inPerM
                + (Math.max(0, completionTokens) / 1_000_000.0) * outPerM;
    }

    public static long pointsForTokens(SystemModel model, int promptTokens, int completionTokens) {
        int pPrompt = model != null ? Math.max(0, model.getPointsPer1kPromptTokens()) : 1;
        int pComp = model != null ? Math.max(0, model.getPointsPer1kCompletionTokens()) : 3;
        long a = (long) Math.ceil(Math.max(0, promptTokens) / 1000.0 * pPrompt);
        long b = (long) Math.ceil(Math.max(0, completionTokens) / 1000.0 * pComp);
        return a + b;
    }

    private static double pick(SystemModel model, boolean peak, Double peakVal, Double offVal,
                              double defaultPeak, double defaultOff) {
        if (peak) {
            if (peakVal != null && peakVal > 0) return peakVal;
            return defaultPeak;
        }
        if (offVal != null && offVal > 0) return offVal;
        return defaultOff;
    }
}
