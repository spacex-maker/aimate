package com.openforge.aimate.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final CaptchaService captchaService;

    public AdminAuthController(AdminAuthService adminAuthService, CaptchaService captchaService) {
        this.adminAuthService = adminAuthService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public CaptchaService.CaptchaPayload captcha() {
        return captchaService.create();
    }

    @PostMapping("/login")
    public ResponseEntity<AdminAuthService.AdminAuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        try {
            return ResponseEntity.ok(adminAuthService.login(
                    request.email(),
                    request.password(),
                    request.captchaId(),
                    request.captchaCode()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /** 账号中心控制台「后台」跳转：ticket 换运营后台会话。 */
    @PostMapping("/staff-handoff")
    public ResponseEntity<AdminAuthService.AdminAuthResponse> staffHandoff(
            @Valid @RequestBody StaffHandoffRequest request
    ) {
        try {
            return ResponseEntity.ok(adminAuthService.loginViaStaffHandoff(request.ticket()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/me")
    public AdminAuthService.AdminMeResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        long adminId;
        try {
            adminId = Long.parseLong(String.valueOf(auth.getPrincipal()));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效会话");
        }
        try {
            return adminAuthService.me(adminId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String captchaId,
            @NotBlank String captchaCode
    ) {}

    public record StaffHandoffRequest(@NotBlank String ticket) {}
}
