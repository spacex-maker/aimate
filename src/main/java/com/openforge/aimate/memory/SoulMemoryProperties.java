package com.openforge.aimate.memory;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aimate.soul-memory")
public class SoulMemoryProperties {

    /** 开启后 recall/store 走 Soul；并关闭 Chat 侧 dump 写入 */
    private boolean enabled = false;

    private String apiBase = "https://soul-api.aimatex.com/api/v1/soul";
    private String platformEnsureUrl = "https://soul-api.aimatex.com/api/v1/platform/souls/ensure";
    private String platformKey = "";
    private String platformCode = "aimatex-chat";

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 20000;
}
