package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.AdminUserListItemDto;
import com.openforge.aimate.agent.dto.BatchAdminUsersRequest;
import com.openforge.aimate.agent.dto.BatchAdminUsersResponse;
import com.openforge.aimate.agent.dto.UpdateAdminUserRequest;
import com.openforge.aimate.billing.ChatPaymentService;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 管理员用户管理：分页列表、启用/禁用、角色调整、批量充值（系统赠送订单）。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int BATCH_MAX = 100;

    private final UserRepository userRepository;
    private final ChatPaymentService chatPaymentService;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q
    ) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        String keyword = q != null ? q.trim() : "";
        Page<User> result = userRepository.search(
                keyword,
                PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "id"))
        );
        Map<String, Object> m = new HashMap<>();
        m.put("items", result.getContent().stream().map(AdminUserListItemDto::from).toList());
        m.put("page", p);
        m.put("size", s);
        m.put("total", result.getTotalElements());
        m.put("totalPages", result.getTotalPages());
        return m;
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

    /**
     * 批量：ENABLE / DISABLE / RECHARGE（系统赠送订单入账）。
     * RECHARGE 按用户独立事务；部分失败不回滚已成功项。
     */
    @PostMapping("/batch")
    public BatchAdminUsersResponse batch(@RequestBody BatchAdminUsersRequest request) {
        if (request == null || request.userIds() == null || request.userIds().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "请选择至少一个用户");
        }
        if (request.userIds().size() > BATCH_MAX) {
            throw new ResponseStatusException(BAD_REQUEST, "单次最多操作 " + BATCH_MAX + " 个用户");
        }
        String action = request.action() == null ? "" : request.action().trim().toUpperCase();
        return switch (action) {
            case "ENABLE" -> batchSetStatus(request.userIds(), User.Status.ACTIVE);
            case "DISABLE" -> batchSetStatus(request.userIds(), User.Status.DISABLED);
            case "RECHARGE" -> batchRecharge(request.userIds(), request.points(), request.remark());
            default -> throw new ResponseStatusException(BAD_REQUEST, "无效的 action，应为 ENABLE / DISABLE / RECHARGE");
        };
    }

    private BatchAdminUsersResponse batchSetStatus(List<Long> userIds, User.Status status) {
        int ok = 0;
        List<BatchAdminUsersResponse.ItemError> errors = new ArrayList<>();
        for (Long id : userIds) {
            if (id == null) {
                errors.add(new BatchAdminUsersResponse.ItemError(0, "无效用户 id"));
                continue;
            }
            try {
                User user = userRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
                user.setStatus(status);
                userRepository.save(user);
                ok++;
            } catch (Exception e) {
                errors.add(new BatchAdminUsersResponse.ItemError(id, e.getMessage() != null ? e.getMessage() : "失败"));
            }
        }
        return new BatchAdminUsersResponse(ok, errors.size(), errors);
    }

    private BatchAdminUsersResponse batchRecharge(List<Long> userIds, Long points, String remark) {
        if (points == null || points <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "充值积分必须大于 0");
        }
        int ok = 0;
        List<BatchAdminUsersResponse.ItemError> errors = new ArrayList<>();
        for (Long id : userIds) {
            if (id == null) {
                errors.add(new BatchAdminUsersResponse.ItemError(0, "无效用户 id"));
                continue;
            }
            try {
                if (!userRepository.existsById(id)) {
                    throw new IllegalArgumentException("用户不存在");
                }
                chatPaymentService.createSystemGiftOrder(id, points, remark);
                ok++;
            } catch (Exception e) {
                errors.add(new BatchAdminUsersResponse.ItemError(id, e.getMessage() != null ? e.getMessage() : "失败"));
            }
        }
        return new BatchAdminUsersResponse(ok, errors.size(), errors);
    }
}
