package com.openforge.aimate.auth;

import com.openforge.aimate.admin.AdminAccount;
import com.openforge.aimate.admin.AdminAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the JWT from the Authorization header on every request.
 * Admin tokens (typ=admin) resolve against admin_users; user tokens keep role claim.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AdminAccountRepository adminAccountRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtUtil.isAdminToken(token)) {
                    authenticateAdmin(request, token);
                } else {
                    authenticateUser(request, token);
                }
            }
        }

        chain.doFilter(request, response);
    }

    private void authenticateAdmin(HttpServletRequest request, String token) {
        Long adminId = jwtUtil.extractUserId(token);
        if (adminId == null) {
            return;
        }
        AdminAccount admin = adminAccountRepository.findById(adminId).orElse(null);
        if (admin == null || !admin.isAccountEnabled()) {
            return;
        }
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(adminId, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("[JWT] Authenticated adminId={} path={}", adminId, request.getRequestURI());
    }

    private void authenticateUser(HttpServletRequest request, String token) {
        Long   userId   = jwtUtil.extractUserId(token);
        String username = jwtUtil.extractUsername(token);

        if (userId == null || username == null) {
            return;
        }

        // 用户 JWT 即使历史 role=ADMIN 也不再授予后台权限（后台走独立 admin_users）
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("USER"));
        var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("[JWT] Authenticated userId={} path={}", userId, request.getRequestURI());
    }
}
