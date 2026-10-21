package com.openforge.aimate.admin;

import com.openforge.aimate.auth.JwtUtil;
import com.openforge.aimate.auth.StaffHandoffService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminAuthService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;
    private final StaffHandoffService staffHandoffService;

    public AdminAuthService(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            CaptchaService captchaService,
            StaffHandoffService staffHandoffService
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.captchaService = captchaService;
        this.staffHandoffService = staffHandoffService;
    }

    @Transactional
    public AdminAuthResponse login(String email, String password, String captchaId, String captchaCode) {
        captchaService.consume(captchaId, captchaCode);
        AdminAccount admin = adminAccountRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("邮箱或密码错误"));
        if (!admin.isAccountEnabled()) {
            throw new IllegalArgumentException("账号已禁用");
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        admin.setLastLoginAt(Instant.now());
        return toAuth(admin);
    }

    /**
     * 账号中心「后台」快捷登录：核销票据，进入独立运营账号会话（不落 users）。
     */
    @Transactional
    public AdminAuthResponse loginViaStaffHandoff(String ticket) {
        StaffHandoffService.HandoffClaims claims = staffHandoffService.consume(ticket);
        String email = normalizeEmail(claims.email());
        String displayName = StringUtils.hasText(claims.displayName())
                ? claims.displayName().trim()
                : email;
        if (displayName.length() > 128) {
            displayName = displayName.substring(0, 128);
        }
        final String name = displayName;
        AdminAccount admin = adminAccountRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createStaffAdmin(email, name));
        if (!admin.isAccountEnabled()) {
            throw new IllegalStateException("管理员账号已禁用");
        }
        admin.setDisplayName(name);
        admin.setLastLoginAt(Instant.now());
        admin = adminAccountRepository.save(admin);
        return toAuth(admin);
    }

    @Transactional(readOnly = true)
    public AdminMeResponse me(long adminId) {
        AdminAccount admin = require(adminId);
        return new AdminMeResponse(
                admin.getId(),
                admin.getEmail(),
                admin.getDisplayName(),
                admin.isAccountEnabled()
        );
    }

    public AdminAccount require(long adminId) {
        return adminAccountRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
    }

    private AdminAccount createStaffAdmin(String email, String displayName) {
        AdminAccount admin = new AdminAccount();
        admin.setEmail(email);
        // 随机占位哈希：仅允许 SSO / 后续设密，不能用空密码登录
        admin.setPasswordHash(passwordEncoder.encode("sso-" + UUID.randomUUID()));
        admin.setDisplayName(displayName);
        admin.setAccountEnabled(true);
        return adminAccountRepository.save(admin);
    }

    private AdminAuthResponse toAuth(AdminAccount admin) {
        String token = jwtUtil.generateAdmin(admin.getId(), admin.getEmail());
        return new AdminAuthResponse(
                token,
                admin.getId(),
                admin.getEmail(),
                admin.getDisplayName()
        );
    }

    private static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AdminAuthResponse(
            String accessToken,
            Long adminId,
            String email,
            String displayName
    ) {}

    public record AdminMeResponse(
            Long id,
            String email,
            String displayName,
            boolean accountEnabled
    ) {}
}
