package com.openforge.aimate.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat 对话图片持久化：用户上传与回复中的图片都落到 Chat 专用桶，库内只存 HTTPS。
 */
@Service
public class ChatCosStorageService {

    private static final Logger log = LoggerFactory.getLogger(ChatCosStorageService.class);

    private static final Pattern DATA_URL = Pattern.compile(
            "^data:image/(jpeg|jpg|png|gif|webp);base64,([A-Za-z0-9+/=\\r\\n]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_DATA = Pattern.compile(
            "data:image/(jpeg|jpg|png|gif|webp);base64,([A-Za-z0-9+/=\\r\\n]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MD_IMAGE = Pattern.compile(
            "!\\[[^\\]]*\\]\\((https?://[^)\\s]+)\\)",
            Pattern.CASE_INSENSITIVE);

    private final ChatCosProperties cos;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ChatCosStorageService(ChatCosProperties cos) {
        this.cos = cos;
    }

    public boolean isUsable() {
        return cos.isEnabled()
                && StringUtils.hasText(cos.getSecretId())
                && StringUtils.hasText(cos.getSecretKey())
                && StringUtils.hasText(cos.getBucket());
    }

    public void assertUsable() {
        if (!isUsable()) {
            throw new IllegalStateException("Chat COS 未配置（AIMATEX_CHAT_COS_* / TENCENTCLOUD_SECRET_*）");
        }
    }

    /** 用户附件：data URL 或已有 HTTPS → 永久 COS URL。 */
    public List<String> persistUserImages(List<String> images) {
        if (images == null || images.isEmpty()) return List.of();
        assertUsable();
        List<String> out = new ArrayList<>(images.size());
        for (String item : images) {
            if (item == null || item.isBlank()) continue;
            String trimmed = item.trim();
            if (isOurCosUrl(trimmed)) {
                out.add(trimmed);
                continue;
            }
            Matcher data = DATA_URL.matcher(trimmed);
            if (data.matches()) {
                String mime = mimeOf(data.group(1));
                byte[] bytes = Base64.getDecoder().decode(data.group(2).replaceAll("\\s", ""));
                out.add(uploadBytes(bytes, mime, "upload"));
                continue;
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                out.add(rehostRemote(trimmed, "upload"));
                continue;
            }
            throw new IllegalArgumentException("图片须为 data URL 或 http(s) 地址");
        }
        return out;
    }

    /**
     * 把回复正文里的内联 data 图、外链图转存到 COS，便于历史回显。
     * DeepSeek 当前多为看图不返图；工具链或其它模型若带图也会落桶。
     */
    public String persistImagesInText(String content) {
        if (content == null || content.isBlank() || !isUsable()) return content;
        String rewritten = content;

        Matcher dataMatcher = INLINE_DATA.matcher(rewritten);
        StringBuffer dataBuf = new StringBuffer();
        while (dataMatcher.find()) {
            try {
                String mime = mimeOf(dataMatcher.group(1));
                byte[] bytes = Base64.getDecoder().decode(dataMatcher.group(2).replaceAll("\\s", ""));
                String url = uploadBytes(bytes, mime, "reply");
                dataMatcher.appendReplacement(dataBuf, Matcher.quoteReplacement(url));
            } catch (Exception e) {
                log.warn("[ChatCos] skip inline data image: {}", e.getMessage());
                dataMatcher.appendReplacement(dataBuf, Matcher.quoteReplacement(dataMatcher.group(0)));
            }
        }
        dataMatcher.appendTail(dataBuf);
        rewritten = dataBuf.toString();

        Matcher mdMatcher = MD_IMAGE.matcher(rewritten);
        StringBuffer mdBuf = new StringBuffer();
        while (mdMatcher.find()) {
            String src = mdMatcher.group(1);
            if (isOurCosUrl(src)) {
                mdMatcher.appendReplacement(mdBuf, Matcher.quoteReplacement(mdMatcher.group(0)));
                continue;
            }
            try {
                String url = rehostRemote(src, "reply");
                String replaced = mdMatcher.group(0).replace(src, url);
                mdMatcher.appendReplacement(mdBuf, Matcher.quoteReplacement(replaced));
            } catch (Exception e) {
                log.warn("[ChatCos] skip markdown image {}: {}", src, e.getMessage());
                mdMatcher.appendReplacement(mdBuf, Matcher.quoteReplacement(mdMatcher.group(0)));
            }
        }
        mdMatcher.appendTail(mdBuf);
        return mdBuf.toString();
    }

    public String publicObjectUrl(String objectKey) {
        return "https://" + cos.getBucket() + ".cos." + cos.getRegion() + ".myqcloud.com/" + objectKey;
    }

    public boolean isOurCosUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String host = cos.getBucket() + ".cos." + cos.getRegion() + ".myqcloud.com";
        return url.contains(host);
    }

    private String rehostRemote(String url, String slot) {
        if (isOurCosUrl(url)) return url;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("下载失败 HTTP " + resp.statusCode());
            }
            String contentType = resp.headers().firstValue("content-type").orElse("image/jpeg");
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IllegalStateException("不是图片: " + contentType);
            }
            byte[] bytes;
            try (InputStream in = resp.body(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                in.transferTo(bos);
                bytes = bos.toByteArray();
            }
            if (bytes.length == 0 || bytes.length > 8L * 1024 * 1024) {
                throw new IllegalStateException("图片过大或为空");
            }
            String mime = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (mime.equals("image/jpg")) mime = "image/jpeg";
            return uploadBytes(bytes, mime, slot);
        } catch (Exception e) {
            throw new IllegalStateException("转存外链图片失败: " + e.getMessage(), e);
        }
    }

    private String uploadBytes(byte[] bytes, String mime, String slot) {
        String key = objectKey(slot, mime);
        CosV5UploadHelper.putStream(
                cos.getSecretId(),
                cos.getSecretKey(),
                cos.getRegion(),
                cos.getBucket(),
                key,
                new ByteArrayInputStream(bytes),
                bytes.length,
                mime,
                true
        );
        return publicObjectUrl(key);
    }

    private String objectKey(String slot, String mime) {
        String safeSlot = (slot == null ? "misc" : slot.trim().toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9_-]", "");
        if (safeSlot.isBlank()) safeSlot = "misc";
        return normalizePrefix() + safeSlot + "/" + UUID.randomUUID() + extensionOf(mime);
    }

    private String normalizePrefix() {
        String p = cos.getPathPrefix();
        if (p == null || p.isBlank()) return "chat/";
        p = p.replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        if (!p.endsWith("/")) p = p + "/";
        return p;
    }

    private static String mimeOf(String subtype) {
        String s = subtype.equalsIgnoreCase("jpg") ? "jpeg" : subtype.toLowerCase(Locale.ROOT);
        return "image/" + s;
    }

    private static String extensionOf(String mime) {
        if (mime == null) return ".jpg";
        String m = mime.toLowerCase(Locale.ROOT);
        if (m.contains("png")) return ".png";
        if (m.contains("webp")) return ".webp";
        if (m.contains("gif")) return ".gif";
        return ".jpg";
    }
}
