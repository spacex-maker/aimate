package com.openforge.aimate.agent;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 用户上传图片：只接受 JPEG / PNG / GIF / WebP 的 data URL。 */
public final class ChatImages {

    public static final int MAX_IMAGES = 4;
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    private static final Pattern DATA_URL = Pattern.compile(
            "^data:image/(jpeg|jpg|png|gif|webp);base64,([A-Za-z0-9+/=\\r\\n]+)$",
            Pattern.CASE_INSENSITIVE);

    private ChatImages() {}

    public static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        if (raw.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("最多上传 " + MAX_IMAGES + " 张图片");
        }
        List<String> out = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) continue;
            String trimmed = item.trim();
            if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
                out.add(trimmed);
                continue;
            }
            Matcher matcher = DATA_URL.matcher(trimmed);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("图片须为 JPEG、PNG、GIF 或 WebP");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(matcher.group(2).replaceAll("\\s", ""));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("图片内容无法解析");
            }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("单张图片需小于 4MB");
            }
            String mime = matcher.group(1).equalsIgnoreCase("jpg") ? "jpeg" : matcher.group(1).toLowerCase();
            out.add("data:image/" + mime + ";base64," + matcher.group(2).replaceAll("\\s", ""));
        }
        return out;
    }
}
