package com.openforge.aimate.systemagent;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system-agents")
@RequiredArgsConstructor
public class AdminSystemAgentController {

    private final SystemAgentService systemAgentService;

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean enabled
    ) {
        return systemAgentService.list(q, type, enabled);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return systemAgentService.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return systemAgentService.save(null, body);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return systemAgentService.save(id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        systemAgentService.delete(id);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/run")
    public Map<String, Object> runNow(@PathVariable Long id) {
        systemAgentService.runManual(id);
        return Map.of("ok", true, "message", "已触发执行");
    }

    @GetMapping("/{id}/runs")
    public Map<String, Object> runs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return systemAgentService.pageRuns(id, page, size);
    }
}
