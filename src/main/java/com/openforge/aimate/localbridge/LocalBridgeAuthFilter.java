package com.openforge.aimate.localbridge;

import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.agent.UserAgentService;
import com.openforge.aimate.repository.UserAgentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Authenticates {@code Authorization: Bearer lba_…} for /api/local-bridge/**.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalBridgeAuthFilter extends OncePerRequestFilter {

    public static final String TOKEN_PREFIX = "lba_";
    public static final String AUTHORITY = "LOCAL_BRIDGE";

    private final UserAgentRepository userAgentRepository;
    private final UserAgentService userAgentService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/local-bridge/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                if (token.startsWith(TOKEN_PREFIX)) {
                    authenticate(request, token);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        String hash = sha256Hex(token);
        UserAgent ua = userAgentRepository.findByLocalBridgeTokenHash(hash).orElse(null);
        if (!userAgentService.isUsable(ua)) {
            return;
        }
        var principal = new LocalBridgePrincipal(ua.getId(), ua.getUserId());
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(AUTHORITY))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("[LocalBridge] auth agentId={} path={}", ua.getId(), request.getRequestURI());
    }

    static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static boolean looksLikeToken(String raw) {
        return StringUtils.hasText(raw) && raw.startsWith(TOKEN_PREFIX) && raw.length() >= 20;
    }
}
