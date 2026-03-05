package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.AdminUserListItemDto;
import com.openforge.aimate.agent.dto.UpdateAdminUserRequest;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 管理员用户管理：列表、启用/禁用、角色调整。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<AdminUserListItemDto>> listAll() {
        List<AdminUserListItemDto> list = userRepository.findAll().stream()
                .map(AdminUserListItemDto::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserListItemDto> update(
            @PathVariable Long id,
            @RequestBody UpdateAdminUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "用户不存在: " + id));

        if (request.status() != null && !request.status().isBlank()) {
            try {
                user.setStatus(User.Status.valueOf(request.status().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(BAD_REQUEST, "无效的 status，应为 ACTIVE 或 DISABLED");
            }
        }
        if (request.role() != null && !request.role().isBlank()) {
            try {
                user.setRole(User.Role.valueOf(request.role().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(BAD_REQUEST, "无效的 role，应为 USER 或 ADMIN");
            }
        }

        user = userRepository.save(user);
        return ResponseEntity.ok(AdminUserListItemDto.from(user));
    }
}
