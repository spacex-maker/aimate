package com.openforge.aimate.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat 项目独立 COS 桶。与 NAS / Account 等子系统分桶，密钥可共用腾讯云主账号。
 */
@ConfigurationProperties(prefix = "aimatex.chat.cos")
public class ChatCosProperties {

    private boolean enabled = true;
    private String secretId = "";
    private String secretKey = "";
    private String bucket = "aimatex-chat-1258150206";
    private String region = "ap-nanjing";
    private String pathPrefix = "chat/";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }
}
