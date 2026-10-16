package com.openforge.aimate.auth;

import com.openforge.aimate.auth.dto.AuthResponse;
import com.openforge.aimate.auth.dto.LoginRequest;
import com.openforge.aimate.auth.dto.RegisterRequest;
import com.openforge.aimate.auth.oidc.AccountOidcService;
import com.openforge.aimate.config.OidcProperties;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OidcProperties oidcProperties;
    private final AccountOidcService accountOidcService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (oidcProperties.isEnabled()) {
            throw new IllegalStateException("请使用 AIMATEX 统一登录");
        }
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (req.email() != null && !req.email().isBlank() && userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("邮箱已被使用");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(normalize(req.email()));
        user.setDisplayName(req.displayName() == null || req.displayName().isBlank()
                ? req.username()
                : req.displayName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus(User.Status.ACTIVE);

        User saved = userRepository.save(user);
        log.info("[Auth] New user registered: id={}, username={}", saved.getId(), saved.getUsername());

        String token = jwtUtil.generate(saved.getId(), saved.getUsername(), saved.getRole().name());
        return new AuthResponse(saved.getId(), saved.getUsername(), saved.getDisplayName(), saved.getRole().name(), token);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        if (oidcProperties.isEnabled()) {
            throw new IllegalStateException("请使用 AIMATEX 统一登录");
        }
        User user = userRepository
                .findByUsernameOrEmail(req.identifier(), req.identifier())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在或密码错误"));

        if (user.getStatus() == User.Status.DISABLED) {
            throw new IllegalStateException("账号已被禁用");
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户不存在或密码错误");
        }

        user.setLastLoginTime(LocalDateTime.now());

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name(), token);
    }

    @Transactional
    public AuthResponse loginWithOidc(String code, String codeVerifier, String redirectUri) {
        if (!oidcProperties.isEnabled()) {
            throw new IllegalArgumentException("统一登录未启用");
        }
        String uri = StringUtils.hasText(redirectUri) ? redirectUri.trim() : oidcProperties.getRedirectUri();
        if (!allowedRedirectUris().contains(uri)) {
            throw new IllegalArgumentException("redirect_uri 未登记");
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(codeVerifier)) {
            throw new IllegalArgumentException("缺少授权码或 code_verifier");
        }
        AccountOidcService.TokenBundle tokens =
                accountOidcService.exchangeAuthorizationCode(code.trim(), codeVerifier.trim(), uri);
        AccountOidcService.AccountPrincipal principal =
                accountOidcService.validateAccessToken(tokens.accessToken());

        User user = userRepository.findByAimatexAccountSub(principal.subject())
                .or(() -> userRepository.findByEmailIgnoreCase(principal.email()))
                .orElseGet(() -> createOidcUser(principal));

        if (user.getStatus() == User.Status.DISABLED) {
            throw new IllegalStateException("账号已被禁用");
        }
        if (!StringUtils.hasText(user.getAimatexAccountSub())) {
            user.setAimatexAccountSub(principal.subject());
        } else if (!principal.subject().equals(user.getAimatexAccountSub())) {
            throw new IllegalArgumentException("该邮箱已绑定其他 AIMATEX 账号");
        }
        if (StringUtils.hasText(principal.displayName())) {
            user.setDisplayName(principal.displayName().trim());
        }
        if (StringUtils.hasText(principal.email()) && !StringUtils.hasText(user.getEmail())) {
            user.setEmail(principal.email().trim().toLowerCase());
        }
        user.setLastLoginTime(LocalDateTime.now());
        user = userRepository.save(user);

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name(), token);
    }

    private User createOidcUser(AccountOidcService.AccountPrincipal principal) {
        User user = new User();
        String email = principal.email().trim().toLowerCase();
        user.setEmail(email);
        user.setUsername(uniqueUsernameFromEmail(email));
        user.setPasswordHash(null);
        user.setAimatexAccountSub(principal.subject());
        user.setDisplayName(StringUtils.hasText(principal.displayName())
                ? principal.displayName().trim()
                : email);
        user.setStatus(User.Status.ACTIVE);
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }

    private String uniqueUsernameFromEmail(String email) {
        String base = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        if (!StringUtils.hasText(base)) {
            base = "user";
        }
        String candidate = base;
        int i = 0;
        while (userRepository.existsByUsername(candidate)) {
            i++;
            candidate = base + "_" + i;
            if (candidate.length() > 64) {
                candidate = base.substring(0, Math.max(1, 64 - 2 - String.valueOf(i).length())) + "_" + i;
            }
        }
        return candidate;
    }

    public List<String> redirectUriList() {
        return List.copyOf(allowedRedirectUris());
    }

    private Set<String> allowedRedirectUris() {
        String raw = StringUtils.hasText(oidcProperties.getRedirectUris())
                ? oidcProperties.getRedirectUris()
                : oidcProperties.getRedirectUri();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
