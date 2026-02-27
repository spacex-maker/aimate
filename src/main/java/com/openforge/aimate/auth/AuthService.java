package com.openforge.aimate.auth;

import com.openforge.aimate.auth.dto.AuthResponse;
import com.openforge.aimate.auth.dto.LoginRequest;
import com.openforge.aimate.auth.dto.RegisterRequest;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
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

        String token = jwtUtil.generate(saved.getId(), saved.getUsername());
        return new AuthResponse(saved.getId(), saved.getUsername(), saved.getDisplayName(), token);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository
                .findByUsernameOrEmail(req.identifier(), req.identifier())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在或密码错误"));

        if (user.getStatus() == User.Status.DISABLED) {
            throw new IllegalStateException("账号已被禁用");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户不存在或密码错误");
        }

        user.setLastLoginTime(LocalDateTime.now());

        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(user.getId(), user.getUsername(), user.getDisplayName(), token);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}

