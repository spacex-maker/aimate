package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ComponentStatusDto;
import com.openforge.aimate.config.ComponentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员查看各组件（MySQL、Milvus、LLM、Embedding、Docker）连接状态，
 * 与启动摘要一致，供容器监控页顶部展示。
 */
@RestController
@RequestMapping("/api/admin/component-status")
@RequiredArgsConstructor
public class AdminComponentStatusController {

    private final ComponentStatusService componentStatusService;

    @GetMapping
    public ResponseEntity<ComponentStatusDto> getComponentStatus() {
        return ResponseEntity.ok(componentStatusService.getStatus());
    }
}
