package com.openforge.aimate.office;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openforge.aimate.domain.OfficePlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 腾讯云 CVM（TC3 签名 HTTP）。未配置 VPC 时走 mock。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficeCvmClient {

    private final OfficeCvmProperties props;
    private final ObjectMapper objectMapper;
    private final AtomicInteger mockSeq = new AtomicInteger(20);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public record ProvisionResult(String instanceId, String privateIp) {}

    public ProvisionResult runInstance(OfficePlan plan, String officeToken, Long userId) {
        if (!props.isConfigured()) {
            int n = mockSeq.incrementAndGet();
            String id = "ins-mock-" + userId + "-" + UUID.randomUUID().toString().substring(0, 6);
            String ip = "10.0.0." + (n % 250);
            log.info("[OfficeCVM] mock provision user={} plan={} id={} ip={}", userId, plan.getCode(), id, ip);
            return new ProvisionResult(id, ip);
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode placement = body.putObject("Placement");
            placement.put("Zone", props.getRegion() + "-1");
            body.put("InstanceType", plan.getInstanceType());
            body.put("ImageId", props.getImageId());
            body.put("InstanceCount", 1);
            ObjectNode disk = body.putObject("SystemDisk");
            disk.put("DiskType", "CLOUD_BSSD");
            disk.put("DiskSize", Math.max(50, plan.getDiskGb()));
            ObjectNode vpc = body.putObject("VirtualPrivateCloud");
            vpc.put("VpcId", props.getVpcId());
            vpc.put("SubnetId", props.getSubnetId());
            ObjectNode net = body.putObject("InternetAccessible");
            net.put("InternetMaxBandwidthOut", 0);
            net.put("PublicIpAssigned", false);
            ArrayNode sgs = body.putArray("SecurityGroupIds");
            sgs.add(props.getSecurityGroupId());
            if (StringUtils.hasText(props.getKeyIds())) {
                ArrayNode keys = body.putArray("KeyIds");
                for (String k : props.getKeyIds().split(",")) {
                    if (StringUtils.hasText(k)) keys.add(k.trim());
                }
            }
            String userData = Base64.getEncoder().encodeToString(
                    buildCloudInit(officeToken).getBytes(StandardCharsets.UTF_8));
            body.put("UserData", userData);
            body.put("InstanceName", "aimate-office-" + userId);

            JsonNode resp = call("RunInstances", body.toString());
            JsonNode ids = resp.path("Response").path("InstanceIdSet");
            if (!ids.isArray() || ids.isEmpty()) {
                throw new IllegalStateException("RunInstances 未返回实例: " + resp);
            }
            return new ProvisionResult(ids.get(0).asText(), null);
        } catch (Exception e) {
            log.error("[OfficeCVM] RunInstances failed: {}", e.getMessage(), e);
            throw new IllegalStateException("创建办公室 CVM 失败: " + e.getMessage(), e);
        }
    }

    public String waitPrivateIp(String instanceId, int maxWaitSeconds) {
        if (!props.isConfigured() || instanceId == null || instanceId.startsWith("ins-mock-")) {
            return null;
        }
        long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L;
        try {
            while (System.currentTimeMillis() < deadline) {
                ObjectNode body = objectMapper.createObjectNode();
                ArrayNode filters = body.putArray("Filters");
                ObjectNode f = filters.addObject();
                f.put("Name", "instance-id");
                f.putArray("Values").add(instanceId);
                JsonNode resp = call("DescribeInstances", body.toString());
                JsonNode set = resp.path("Response").path("InstanceSet");
                if (set.isArray() && !set.isEmpty()) {
                    JsonNode ips = set.get(0).path("PrivateIpAddresses");
                    if (ips.isArray() && !ips.isEmpty()) {
                        return ips.get(0).asText();
                    }
                }
                Thread.sleep(3000L);
            }
        } catch (Exception e) {
            log.warn("[OfficeCVM] waitPrivateIp failed: {}", e.getMessage());
        }
        return null;
    }

    public void terminate(String instanceId) {
        if (!StringUtils.hasText(instanceId) || instanceId.startsWith("ins-mock-") || !props.isConfigured()) {
            log.info("[OfficeCVM] skip terminate id={}", instanceId);
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.putArray("InstanceIds").add(instanceId);
            call("TerminateInstances", body.toString());
        } catch (Exception e) {
            throw new IllegalStateException("释放办公室 CVM 失败: " + e.getMessage(), e);
        }
    }

    private JsonNode call(String action, String payload) throws Exception {
        String host = "cvm.tencentcloudapi.com";
        String service = "cvm";
        String version = "2017-03-12";
        String region = props.getRegion();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(Long.parseLong(timestamp)));

        String hashedPayload = sha256Hex(payload);
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + host + "\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload;
        String credentialScope = date + "/" + service + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);
        byte[] secretDate = hmac(("TC3" + props.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac(secretDate, service);
        byte[] secretSigning = hmac(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmac(secretSigning, stringToSign));
        String authorization = "TC3-HMAC-SHA256 Credential=" + props.getSecretId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Host", host)
                .header("X-TC-Action", action)
                .header("X-TC-Version", version)
                .header("X-TC-Timestamp", timestamp)
                .header("X-TC-Region", region)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(resp.body());
        if (node.path("Response").has("Error")) {
            throw new IllegalStateException(node.path("Response").path("Error").toString());
        }
        return node;
    }

    private String buildCloudInit(String officeToken) {
        int port = props.getAgentPort();
        // 使用独立脚本文件内容（避免 cloud-init 过长）；内联精简版
        return """
                #!/bin/bash
                set -e
                export OFFICE_TOKEN='%s'
                export OFFICE_AGENT_PORT=%d
                mkdir -p /opt/aimate-office /home/ubuntu/aimate-learning
                curl -fsSL -o /opt/aimate-office/agent.py https://chat.aimatex.com/office-agent/agent.py 2>/dev/null || true
                if [ ! -s /opt/aimate-office/agent.py ]; then
                cat > /opt/aimate-office/agent.py << 'PY'
                %s
                PY
                fi
                cat > /etc/systemd/system/aimate-office-agent.service << EOF
                [Unit]
                Description=AIMATEX Office Agent
                After=network.target
                [Service]
                Environment=OFFICE_TOKEN=%s
                Environment=OFFICE_AGENT_PORT=%d
                ExecStart=/usr/bin/python3 /opt/aimate-office/agent.py
                Restart=always
                [Install]
                WantedBy=multi-user.target
                EOF
                systemctl daemon-reload
                systemctl enable --now aimate-office-agent || true
                """.formatted(officeToken, port, OfficeAgentScript.PYTHON, officeToken, port);
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }
}
