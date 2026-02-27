package com.openforge.aimate.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the JWT from the Authorization header on every request.
 * If valid, puts an authenticated principal into the SecurityContext
 * so the rest of the filter chain treats the request as authenticated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token    = header.substring(7);
            Long   userId   = jwtUtil.extractUserId(token);
            String username = jwtUtil.extractUsername(token);

            if (userId != null && username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Use userId as the principal, username as a granted authority label
                var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of() // roles/authorities — extend when needed
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("[JWT] Authenticated userId={} path={}", userId, request.getRequestURI());
            }
        }

        chain.doFilter(request, response);
    }
}
