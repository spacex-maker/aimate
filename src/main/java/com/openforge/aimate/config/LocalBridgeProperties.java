package com.openforge.aimate.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aimate.local-bridge")
public class LocalBridgeProperties {

    /** 写入本机 AI 提示词的 Chat API 根，如 https://chat.aimatex.com */
    /** 必须指向 API 域名；chat.aimatex.com 是 SPA，POST /api 会 405 */
    private String publicApiBase = "https://chat-api.aimatex.com";

    /** 领取任务默认轮询间隔建议（秒） */
    private int pollIntervalSeconds = 8;

    /**
     * CLAIMED 租约秒数：到期未 complete 则回收为 PENDING，避免 Worker 挂死占坑。
     * 本机改代码可能较久，默认 15 分钟。
     */
    private int claimLeaseSeconds = 900;

    /** 扫描过期租约的间隔（毫秒） */
    private long leaseReclaimIntervalMs = 60_000L;
}
