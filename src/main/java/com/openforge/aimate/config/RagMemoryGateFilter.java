package com.openforge.aimate.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 记忆总开关关闭时，记忆 HTTP 接口不再进入向量库。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RagMemoryGateFilter extends OncePerRequestFilter {

    private final SystemConfigService systemConfigService;

    public RagMemoryGateFilter(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/memories");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (systemConfigService.isRagMemoryEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"记忆系统已关闭\"}");
    }
}
