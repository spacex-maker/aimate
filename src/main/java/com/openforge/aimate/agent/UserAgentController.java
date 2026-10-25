package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SessionResponse;
import com.openforge.aimate.domain.RentPeriod;
import com.openforge.aimate.memory.SoulMemoryBridge;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserAgentController {

    private final UserAgentService userAgentService;
    private final AgentHireService agentHireService;
    private final AgentCatalogService agentCatalogService;
    private final SecretaryAgentService secretaryAgentService;
    private final SoulMemoryBridge soulMemoryBridge;

    @GetMapping("/agents/catalog")
    public List<Map<String, Object>> catalog() {
        return agentCatalogService.listCatalog(true);
    }

    @GetMapping("/agents/industries")
    public List<Map<String, Object>> industries() {
        return agentCatalogService.listIndustries(true);
    }

    @GetMapping("/agents/careers")
    public List<Map<String, Object>> careers() {
        return agentCatalogService.listCareers(true);
    }

    @GetMapping("/agents/quote")
    public Map<String, Object> quote(
            @RequestParam Long catalogId,
            @RequestParam String period
    ) {
        try {
            return agentHireService.quote(catalogId, RentPeriod.valueOf(period.trim().toUpperCase()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/agents/hire")
    public Map<String, Object> hire(@RequestBody HireBody body) {
        try {
            return agentHireService.hire(
                    requireUserId(),
                    body.catalogId(),
                    RentPeriod.valueOf(body.period().trim().toUpperCase()),
                    body.name(),
                    body.gender()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/agents/{id}/renew")
    public Map<String, Object> renew(@PathVariable Long id, @RequestBody RenewBody body) {
        try {
            return agentHireService.renew(
                    requireUserId(),
                    id,
                    RentPeriod.valueOf(body.period().trim().toUpperCase())
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/me/agents")
    public List<Map<String, Object>> mine() {
        return userAgentService.listMine(requireUserId());
    }

    @GetMapping("/me/agents/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        try {
            return userAgentService.detail(requireUserId(), id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /** 打开与该 Agent 的单聊：有旧会话则复用，否则新建空会话 */
    @PostMapping("/me/agents/{id}/open-chat")
    public SessionResponse openChat(@PathVariable Long id) {
        try {
            return userAgentService.openChat(requireUserId(), id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PatchMapping("/me/agents/{id}")
    public Map<String, Object> patch(@PathVariable Long id, @RequestBody UserAgentService.PersonaBody body) {
        try {
            return userAgentService.updatePersona(requireUserId(), id, body);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/me/agents/{id}/avatar")
    public Map<String, Object> avatar(@PathVariable Long id, @RequestBody AvatarBody body) {
        try {
            boolean clear = body != null && Boolean.TRUE.equals(body.clear());
            String image = body != null ? body.image() : null;
            return userAgentService.updateAvatar(requireUserId(), id, image, clear);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/me/agents/{id}/prompt/exclude-atoms")
    public Map<String, Object> excludeAtoms(@PathVariable Long id, @RequestBody ExcludeBody body) {
        try {
            return userAgentService.excludePromptAtoms(requireUserId(), id, body.atomIds());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/me/agents/secretary/ensure")
    public Map<String, Object> ensureSecretary() {
        try {
            var ua = secretaryAgentService.ensureSecretaryAfterProfile(requireUserId());
            return userAgentService.detail(requireUserId(), ua.getId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 查看某 Agent 绑定 Soul 的记忆预览（不暴露 token） */
    @GetMapping("/me/agents/{id}/soul-memories")
    public Map<String, Object> soulMemories(@PathVariable Long id) {
        try {
            // 校验归属
            userAgentService.detail(requireUserId(), id);
            return soulMemoryBridge.listMemories(requireUserId(), id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }

    /** name / gender 必填：伙伴真名与性别，写入 Soul 后不可更改 */
    public record HireBody(Long catalogId, String period, String name, String gender) {}
    public record RenewBody(String period) {}
    public record ExcludeBody(List<String> atomIds) {}
    public record AvatarBody(String image, Boolean clear) {}
}
