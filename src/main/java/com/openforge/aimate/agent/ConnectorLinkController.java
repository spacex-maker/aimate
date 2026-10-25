package com.openforge.aimate.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me/connectors")
@RequiredArgsConstructor
public class ConnectorLinkController {

    private final ConnectorLinkService connectorLinkService;

    @GetMapping
    public List<Map<String, Object>> list() {
        return connectorLinkService.list(requireUserId());
    }

    @PostMapping("/{careerCode}/enable")
    public Map<String, Object> enable(@PathVariable String careerCode) {
        try {
            return connectorLinkService.enable(requireUserId(), careerCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{careerCode}/disable")
    public Map<String, Object> disable(@PathVariable String careerCode) {
        try {
            return connectorLinkService.disable(requireUserId(), careerCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }
}
