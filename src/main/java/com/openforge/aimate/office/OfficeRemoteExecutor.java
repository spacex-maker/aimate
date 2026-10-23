package com.openforge.aimate.office;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.domain.UserOffice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfficeRemoteExecutor {

    private final OfficeCvmProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String exec(UserOffice office, String command, int timeoutSeconds) {
        JsonNode n = post(office, "/exec", Map.of(
                "command", command,
                "timeout_seconds", timeoutSeconds
        ));
        int code = n.path("exitCode").asInt(-1);
        String out = n.path("stdout").asText("");
        String err = n.path("stderr").asText("");
        String body = (out + (err.isBlank() ? "" : "\n" + err)).trim();
        if (code != 0) {
            return "[ToolError] 办公室执行失败 (exit " + code + "): "
                    + (body.length() > 1500 ? body.substring(0, 1500) + "..." : body);
        }
        return body.isEmpty() ? "命令已在办公室环境执行成功（无输出）。" : body;
    }

    public String install(UserOffice office, List<String> packages) {
        JsonNode n = post(office, "/install", Map.of("packages", packages));
        int code = n.path("exitCode").asInt(-1);
        String out = n.path("stdout").asText("");
        String err = n.path("stderr").asText("");
        String body = (out + (err.isBlank() ? "" : "\n" + err)).trim();
        if (code != 0) {
            return "[ToolError] 办公室安装失败 (exit " + code + "): "
                    + (body.length() > 1500 ? body.substring(0, 1500) + "..." : body);
        }
        return "已在办公室环境安装: " + String.join(" ", packages) + "。\n"
                + (body.length() > 800 ? body.substring(0, 800) + "..." : body);
    }

    public String write(UserOffice office, String path, String content) {
        JsonNode n = post(office, "/write", Map.of("path", path, "content", content));
        if (!n.path("ok").asBoolean(false)) {
            return "[ToolError] 办公室写文件失败: " + n;
        }
        return "已写入办公室文件: " + path;
    }

    public boolean health(UserOffice office) {
        try {
            if (!StringUtils.hasText(office.getPrivateIp())) return office.getCvmInstanceId() != null
                    && office.getCvmInstanceId().startsWith("ins-mock-");
            if (office.getCvmInstanceId() != null && office.getCvmInstanceId().startsWith("ins-mock-")) {
                return true;
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl(office) + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode post(UserOffice office, String path, Map<String, Object> body) {
        try {
            if (office.getCvmInstanceId() != null && office.getCvmInstanceId().startsWith("ins-mock-")) {
                return objectMapper.readTree("{\"ok\":true,\"exitCode\":0,\"stdout\":\"[mock-office] " 
                        + path + " accepted\\n\",\"stderr\":\"\"}");
            }
            if (!StringUtils.hasText(office.getPrivateIp())) {
                throw new IllegalStateException("办公室尚未获得内网 IP");
            }
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl(office) + path))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .header("X-Office-Token", office.getOfficeToken())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IllegalStateException("office-agent HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            log.warn("[OfficeExec] {} failed: {}", path, e.getMessage());
            try {
                return objectMapper.readTree("{\"exitCode\":1,\"stdout\":\"\",\"stderr\":"
                        + objectMapper.writeValueAsString(e.getMessage()) + "}");
            } catch (Exception ex) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    private String baseUrl(UserOffice office) {
        return "http://" + office.getPrivateIp() + ":" + props.getAgentPort();
    }
}
