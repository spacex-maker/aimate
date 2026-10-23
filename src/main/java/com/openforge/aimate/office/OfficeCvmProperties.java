package com.openforge.aimate.office;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aimate.office")
public class OfficeCvmProperties {

    /** 未配置 VPC 时走 mock 开通（本地/缺省运维配置） */
    private boolean mockProvision = true;
    private String region = "ap-guangzhou";
    private String secretId = "";
    private String secretKey = "";
    private String vpcId = "";
    private String subnetId = "";
    private String securityGroupId = "";
    private String imageId = "";
    private String keyIds = "";
    private int agentPort = 18765;
    private String chatPrivateIp = "";
    /** 闲时自主：每 Agent 最少间隔小时 */
    private int idleTickHours = 6;

    public boolean isConfigured() {
        return !mockProvision
                && notBlank(secretId)
                && notBlank(secretKey)
                && notBlank(vpcId)
                && notBlank(subnetId)
                && notBlank(securityGroupId)
                && notBlank(imageId);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public boolean isMockProvision() { return mockProvision; }
    public void setMockProvision(boolean mockProvision) { this.mockProvision = mockProvision; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getVpcId() { return vpcId; }
    public void setVpcId(String vpcId) { this.vpcId = vpcId; }
    public String getSubnetId() { return subnetId; }
    public void setSubnetId(String subnetId) { this.subnetId = subnetId; }
    public String getSecurityGroupId() { return securityGroupId; }
    public void setSecurityGroupId(String securityGroupId) { this.securityGroupId = securityGroupId; }
    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public String getKeyIds() { return keyIds; }
    public void setKeyIds(String keyIds) { this.keyIds = keyIds; }
    public int getAgentPort() { return agentPort; }
    public void setAgentPort(int agentPort) { this.agentPort = agentPort; }
    public String getChatPrivateIp() { return chatPrivateIp; }
    public void setChatPrivateIp(String chatPrivateIp) { this.chatPrivateIp = chatPrivateIp; }
    public int getIdleTickHours() { return idleTickHours; }
    public void setIdleTickHours(int idleTickHours) { this.idleTickHours = idleTickHours; }
}
