package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SystemProviderDto;
import com.openforge.aimate.domain.SystemProvider;
import com.openforge.aimate.repository.SystemProviderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/admin/system-providers")
public class AdminSystemProviderController {

    private final SystemProviderRepository providerRepository;

    public AdminSystemProviderController(SystemProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @GetMapping
    public ResponseEntity<List<SystemProviderDto>> list() {
        return ResponseEntity.ok(
                providerRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                        .map(SystemProviderDto::from)
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<SystemProviderDto> create(@RequestBody UpsertRequest body) {
        if (body == null || !StringUtils.hasText(body.code()) || !StringUtils.hasText(body.displayName())) {
            throw new ResponseStatusException(BAD_REQUEST, "Please fill provider code and display name");
        }
        String code = body.code().trim().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z][a-z0-9_-]{0,62}")) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid provider code");
        }
        if (providerRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(CONFLICT, "Provider already exists: " + code);
        }
        SystemProvider p = new SystemProvider();
        p.setCode(code);
        p.setDisplayName(body.displayName().trim());
        p.setDescription(blankToNull(body.description()));
        p.setEnabled(body.enabled() == null || body.enabled());
        p.setSortOrder(body.sortOrder() == null ? 100 : body.sortOrder());
        if (StringUtils.hasText(body.apiKey())) {
            p.setApiKey(body.apiKey().trim());
        }
        p = providerRepository.save(p);
        return ResponseEntity.ok(SystemProviderDto.from(p));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SystemProviderDto> update(@PathVariable long id, @RequestBody UpsertRequest body) {
        SystemProvider p = providerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Provider not found"));
        if (body == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Empty body");
        }
        if (body.displayName() != null) {
            String name = body.displayName().trim();
            if (!StringUtils.hasText(name)) {
                throw new ResponseStatusException(BAD_REQUEST, "Display name cannot be empty");
            }
            p.setDisplayName(name);
        }
        if (body.description() != null) {
            p.setDescription(blankToNull(body.description()));
        }
        if (body.enabled() != null) {
            p.setEnabled(body.enabled());
        }
        if (body.sortOrder() != null) {
            p.setSortOrder(body.sortOrder());
        }
        // null = leave unchanged; blank = clear
        if (body.apiKey() != null) {
            String key = body.apiKey().trim();
            p.setApiKey(key.isEmpty() ? null : key);
        }
        p = providerRepository.save(p);
        return ResponseEntity.ok(SystemProviderDto.from(p));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!providerRepository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Provider not found");
        }
        providerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public record UpsertRequest(
            String code,
            String displayName,
            String description,
            Boolean enabled,
            Integer sortOrder,
            String apiKey
    ) {}
}