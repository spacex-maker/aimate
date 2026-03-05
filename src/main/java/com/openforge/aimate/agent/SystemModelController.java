package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SystemModelDto;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统模型目录：供前端输入框「模型选择」下拉列表使用。
 */
@RestController
@RequestMapping("/api/agent/system-models")
@RequiredArgsConstructor
public class SystemModelController {

    private final SystemModelRepository systemModelRepository;

    @GetMapping
    public ResponseEntity<List<SystemModelDto>> list() {
        List<SystemModelDto> list = systemModelRepository.findByEnabledTrueOrderBySortOrderAsc()
                .stream()
                .map(SystemModelDto::from)
                .toList();
        return ResponseEntity.ok(list);
    }
}
