package com.openforge.aimate.auth;

import com.openforge.aimate.auth.dto.AuthResponse;
import com.openforge.aimate.auth.dto.LoginRequest;
import com.openforge.aimate.auth.dto.RegisterRequest;
import com.openforge.aimate.config.OidcProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OidcProperties oidcProperties;

    @GetMapping("/oidc/config")
    public Map<String, Object> oidcConfig(HttpServletRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", oidcProperties.isEnabled());
        out.put("clientId", oidcProperties.getClientId());
        out.put("authorizeUrl", oidcProperties.getAuthorizeUrl());
        out.put("scopes", oidcProperties.getScopes());
        out.put("issuer", oidcProperties.getIssuer());
        List<String> allowed = authService.redirectUriList();
        out.put("redirectUris", allowed);
        out.put("redirectUri", resolveRedirectUri(request, allowed));
        return out;
    }

    @PostMapping("/oidc/callback")
    public ResponseEntity<AuthResponse> oidcCallback(@Valid @RequestBody OidcCallbackRequest request) {
        return ResponseEntity.ok(
                authService.loginWithOidc(request.code(), request.codeVerifier(), request.redirectUri())
        );
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (oidcProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.GONE, "请使用 AIMATEX 统一登录");
        }
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        if (oidcProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.GONE, "请使用 AIMATEX 统一登录");
        }
        return ResponseEntity.ok(authService.login(request));
    }

    private String resolveRedirectUri(HttpServletRequest request, List<String> allowed) {
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            String referer = request.getHeader("Referer");
            if (StringUtils.hasText(referer)) {
                try {
                    java.net.URI u = java.net.URI.create(referer);
                    if (StringUtils.hasText(u.getScheme()) && StringUtils.hasText(u.getHost())) {
                        int port = u.getPort();
                        origin = u.getScheme() + "://" + u.getHost()
                                + (port > 0 ? ":" + port : "");
                    }
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        if (StringUtils.hasText(origin)) {
            String candidate = origin.replaceAll("/$", "") + "/oauth/callback";
            if (allowed.contains(candidate)) {
                return candidate;
            }
        }
        return oidcProperties.getRedirectUri();
    }

    public record OidcCallbackRequest(
            @NotBlank String code,
            @NotBlank String codeVerifier,
            String redirectUri
    ) {}
}
