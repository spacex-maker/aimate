package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SystemModelDto;
import com.openforge.aimate.agent.dto.UpdateSystemModelEnabledRequest;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 管理员系统模型管理：查看全部（含已关闭）、开启/关闭模型。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/system-models")
@RequiredArgsConstructor
public class AdminSystemModelController {

    private final SystemModelRepository systemModelRepository;

    @GetMapping
    public ResponseEntity<List<SystemModelDto>> listAll() {
        List<SystemModelDto> list = systemModelRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(SystemModelDto::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SystemModelDto> updateEnabled(
            @PathVariable Long id,
            @RequestBody UpdateSystemModelEnabledRequest request) {
        SystemModel model = systemModelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "系统模型不存在: " + id));
        model.setEnabled(request.enabled());
        model = systemModelRepository.save(model);
        return ResponseEntity.ok(SystemModelDto.from(model));
    }
}
