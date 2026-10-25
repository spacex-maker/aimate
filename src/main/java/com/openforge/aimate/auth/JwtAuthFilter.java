package com.openforge.aimate.auth;

import com.openforge.aimate.admin.AdminAccount;
import com.openforge.aimate.admin.AdminAccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
 * 已登录且接近过期时，在响应头 {@link JwtUtil#HEADER_NEW_TOKEN} 下发续期后的 token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AdminAccountRepository adminAccountRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    Claims claims = jwtUtil.parse(token);
                    String typ = claims.get("typ", String.class);
                    boolean ok = JwtUtil.TYP_ADMIN.equals(typ)
                            ? authenticateAdmin(request, claims)
                            : authenticateUser(request, claims);
                    if (ok && jwtUtil.shouldRenew(claims)) {
                        String renewed = jwtUtil.renewFromClaims(claims);
                        response.setHeader(JwtUtil.HEADER_NEW_TOKEN, renewed);
                        log.debug("[JWT] Renewed token for subject={} path={}",
                                claims.getSubject(), request.getRequestURI());
                    }
                } catch (JwtException | IllegalArgumentException e) {
                    log.debug("[JWT] Reject token: {}", e.getMessage());
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean authenticateAdmin(HttpServletRequest request, Claims claims) {
        Long adminId;
        try {
            adminId = Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return false;
        }
        AdminAccount admin = adminAccountRepository.findById(adminId).orElse(null);
        if (admin == null || !admin.isAccountEnabled()) {
            return false;
        }
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(adminId, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("[JWT] Authenticated adminId={} path={}", adminId, request.getRequestURI());
        return true;
    }

    private boolean authenticateUser(HttpServletRequest request, Claims claims) {
        Long userId;
        String username;
        try {
            userId = Long.valueOf(claims.getSubject());
            username = claims.get("username", String.class);
        } catch (Exception e) {
            return false;
        }

        if (userId == null || username == null) {
            return false;
        }

        // 用户 JWT 即使历史 role=ADMIN 也不再授予后台权限（后台走独立 admin_users）
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("USER"));
        var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("[JWT] Authenticated userId={} path={}", userId, request.getRequestURI());
        return true;
    }
}
