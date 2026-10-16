package com.openforge.aimate.auth.oidc;

import com.openforge.aimate.config.OidcProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AccountOidcService {

    private static final Logger log = LoggerFactory.getLogger(AccountOidcService.class);

    private final OidcProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final AtomicReference<JWKSet> jwkCache = new AtomicReference<>();

    public AccountOidcService(OidcProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public TokenBundle exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", props.getClientId());
        form.add("code_verifier", codeVerifier);
        return postToken(form);
    }

    public TokenBundle refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.getClientId());
        return postToken(form);
    }

    public AccountPrincipal validateAccessToken(String accessToken) {
        try {
            JWTClaimsSet claims = verify(accessToken);
            requireIssuer(claims);
            String sub = claims.getSubject();
            if (!StringUtils.hasText(sub)) {
                throw new IllegalArgumentException("无效的账号令牌");
            }
            String email = stringClaim(claims, "email");
            String name = firstNonBlank(
                    stringClaim(claims, "name"),
                    stringClaim(claims, "nickname"),
                    stringClaim(claims, "preferred_username")
            );
            // picture 主要在 userinfo / id_token；access token 未必带
            String picture = stringClaim(claims, "picture");
            Membership membership = readMembership(claims);
            // 每次登录拉 userinfo，同步昵称与头像（类似微信授权资料）
            JsonNode info = fetchUserinfo(accessToken);
            if (info != null) {
                if (!StringUtils.hasText(email)) {
                    email = str(info.path("email").asText(null));
                }
                name = firstNonBlank(
                        str(info.path("name").asText(null)),
                        str(info.path("nickname").asText(null)),
                        str(info.path("preferred_username").asText(null)),
                        name
                );
                picture = firstNonBlank(str(info.path("picture").asText(null)), picture);
                if (!membership.active() && info.has("membership")) {
                    membership = readMembershipNode(info.get("membership"));
                }
            }
            if (!StringUtils.hasText(email)) {
                throw new IllegalArgumentException("账号令牌缺少邮箱");
            }
            if (!StringUtils.hasText(name)) {
                name = email;
            }
            return new AccountPrincipal(sub, email.trim().toLowerCase(), name, picture, membership);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("validate account token failed: {}", e.getMessage());
            throw new IllegalArgumentException("账号令牌校验失败");
        }
    }

    private JsonNode fetchUserinfo(String accessToken) {
        try {
            String body = restClient.get()
                    .uri(URI.create(props.getUserinfoUrl()))
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.debug("userinfo fetch skipped: {}", e.getMessage());
            return null;
        }
    }

    private TokenBundle postToken(MultiValueMap<String, String> form) {
        try {
            String body = restClient.post()
                    .uri(URI.create(props.getTokenUrl()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            if (json.hasNonNull("error")) {
                String desc = json.path("error_description").asText(json.path("error").asText("换票失败"));
                throw new IllegalArgumentException(desc);
            }
            String access = json.path("access_token").asText(null);
            if (!StringUtils.hasText(access)) {
                throw new IllegalArgumentException("换票失败：未返回 access_token");
            }
            return new TokenBundle(
                    access,
                    json.path("id_token").asText(null),
                    json.path("refresh_token").asText(null),
                    json.path("token_type").asText("Bearer"),
                    json.path("expires_in").asLong(0L)
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("oidc token exchange failed: {}", e.getMessage());
            throw new IllegalArgumentException("统一登录换票失败，请重试");
        }
    }

    private JWTClaimsSet verify(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        JWKSet jwkSet = jwkSet();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(jwkSet);
        JWSKeySelector<SecurityContext> selector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source);
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(selector);
        return processor.process(jwt, null);
    }

    private JWKSet jwkSet() throws Exception {
        JWKSet cached = jwkCache.get();
        if (cached != null) {
            return cached;
        }
        synchronized (jwkCache) {
            cached = jwkCache.get();
            if (cached != null) {
                return cached;
            }
            String json = restClient.get()
                    .uri(URI.create(props.getJwksUrl()))
                    .retrieve()
                    .body(String.class);
            JWKSet loaded = JWKSet.parse(json);
            jwkCache.set(loaded);
            return loaded;
        }
    }

    private void requireIssuer(JWTClaimsSet claims) throws ParseException {
        String iss = claims.getIssuer();
        if (!props.getIssuer().equals(iss)) {
            throw new IllegalArgumentException("令牌 issuer 不匹配");
        }
    }

    private Membership readMembership(JWTClaimsSet claims) {
        Object raw = claims.getClaim("membership");
        if (!(raw instanceof java.util.Map<?, ?> map)) {
            return Membership.inactive();
        }
        return fromMembershipMap(map);
    }

    private Membership readMembershipNode(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Membership.inactive();
        }
        boolean active = node.path("active").asBoolean(false);
        String planCode = str(node.path("planCode").asText(null));
        Instant expiresAt = parseInstant(
                node.has("expiresAt") && !node.get("expiresAt").isNull()
                        ? (node.get("expiresAt").isNumber()
                        ? node.get("expiresAt").numberValue()
                        : node.get("expiresAt").asText(null))
                        : null
        );
        List<String> keys = new ArrayList<>();
        JsonNode bk = node.get("benefitKeys");
        if (bk != null && bk.isArray()) {
            for (JsonNode o : bk) {
                if (o != null && StringUtils.hasText(o.asText())) {
                    keys.add(o.asText());
                }
            }
        }
        return new Membership(active, planCode, expiresAt, Collections.unmodifiableList(keys));
    }

    private Membership fromMembershipMap(java.util.Map<?, ?> map) {
        boolean active = bool(map.get("active"));
        String planCode = str(map.get("planCode"));
        Instant expiresAt = parseInstant(map.get("expiresAt"));
        List<String> keys = new ArrayList<>();
        Object bk = map.get("benefitKeys");
        if (bk instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && StringUtils.hasText(String.valueOf(o))) {
                    keys.add(String.valueOf(o));
                }
            }
        }
        return new Membership(active, planCode, expiresAt, Collections.unmodifiableList(keys));
    }

    private static Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) {
            long x = n.longValue();
            // ms or seconds
            if (x > 10_000_000_000L) {
                return Instant.ofEpochMilli(x);
            }
            return Instant.ofEpochSecond(x);
        }
        String s = String.valueOf(v).trim();
        if (!StringUtils.hasText(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean bool(Object v) {
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            Object v = claims.getClaim(name);
            return str(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return null;
    }

    public record TokenBundle(
            String accessToken,
            String idToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {}

    public record Membership(
            boolean active,
            String planCode,
            Instant expiresAt,
            List<String> benefitKeys
    ) {
        public static Membership inactive() {
            return new Membership(false, null, null, List.of());
        }

        public boolean hasBenefit(String key) {
            if (!StringUtils.hasText(key) || benefitKeys == null) {
                return false;
            }
            return benefitKeys.contains(key);
        }
    }

    public record AccountPrincipal(
            String subject,
            String email,
            String displayName,
            String avatarUrl,
            Membership membership
    ) {}
}
