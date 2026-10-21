package com.openforge.aimate.admin;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final long TTL_SECONDS = 300;

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public CaptchaPayload create() {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        String code = SvgLineCaptcha.randomCode(4);
        store.put(id, new Entry(code, Instant.now().plusSeconds(TTL_SECONDS)));
        return new CaptchaPayload(id, SvgLineCaptcha.toDataUrl(code, 200, 72));
    }

    /** 校验后立即作废 */
    public void consume(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new IllegalArgumentException("请填写图形验证码");
        }
        Entry entry = store.remove(captchaId.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("图形验证码已过期，请刷新");
        }
        if (!entry.code().equalsIgnoreCase(captchaCode.trim())) {
            throw new IllegalArgumentException("图形验证码错误");
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, Entry> e : store.entrySet()) {
            if (e.getValue().expiresAt().isBefore(now)) {
                store.remove(e.getKey(), e.getValue());
            }
        }
    }

    public record CaptchaPayload(String captchaId, String imageBase64) {}

    private record Entry(String code, Instant expiresAt) {}
}
