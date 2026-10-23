package com.openforge.aimate.group;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GroupController {

    private final GroupChatService groupChatService;
    private final GroupMessageService groupMessageService;

    @PostMapping("/groups")
    public Map<String, Object> create(@RequestBody GroupChatService.CreateGroupRequest body) {
        return groupChatService.create(requireUserId(), body);
    }

    @GetMapping("/groups")
    public Object listMine() {
        return groupChatService.listMine(requireUserId());
    }

    @GetMapping("/groups/{publicId}")
    public Map<String, Object> detail(@PathVariable String publicId) {
        return groupChatService.detail(requireUserId(), publicId);
    }

    @PatchMapping("/groups/{publicId}")
    public Map<String, Object> patch(@PathVariable String publicId,
                                     @RequestBody GroupChatService.PatchGroupRequest body) {
        return groupChatService.patch(requireUserId(), publicId, body);
    }

    @PostMapping("/groups/{publicId}/pin")
    public Map<String, Object> pin(@PathVariable String publicId, @RequestBody Map<String, Boolean> body) {
        boolean pinned = body == null || body.get("pinned") == null || Boolean.TRUE.equals(body.get("pinned"));
        return groupChatService.setPinned(requireUserId(), publicId, pinned);
    }

    @PostMapping("/groups/{publicId}/archive")
    public Map<String, Object> archive(@PathVariable String publicId, @RequestBody Map<String, Boolean> body) {
        boolean archived = body == null || body.get("archived") == null || Boolean.TRUE.equals(body.get("archived"));
        return groupChatService.setArchived(requireUserId(), publicId, archived);
    }

    @GetMapping("/groups/{publicId}/invite")
    public Map<String, Object> invite(@PathVariable String publicId) {
        return groupChatService.inviteInfo(requireUserId(), publicId);
    }

    @PostMapping("/groups/{publicId}/invite/refresh")
    public Map<String, Object> refreshInvite(@PathVariable String publicId) {
        return groupChatService.refreshInvite(requireUserId(), publicId);
    }

    @GetMapping("/groups/join/{inviteCode}")
    public Map<String, Object> previewJoin(@PathVariable String inviteCode) {
        return groupChatService.previewInvite(inviteCode);
    }

    @PostMapping("/groups/join/{inviteCode}")
    public Map<String, Object> join(@PathVariable String inviteCode) {
        return groupChatService.joinByInvite(requireUserId(), inviteCode);
    }

    @DeleteMapping("/groups/{publicId}")
    public Map<String, Object> dissolve(@PathVariable String publicId) {
        groupChatService.dissolve(requireUserId(), publicId);
        return Map.of("ok", true);
    }

    @PostMapping("/groups/{publicId}/members")
    public Map<String, Object> addMembers(@PathVariable String publicId,
                                          @RequestBody GroupChatService.AddMembersRequest body) {
        return groupChatService.addMembers(requireUserId(), publicId, body);
    }

    @DeleteMapping("/groups/{publicId}/members/{memberId}")
    public Map<String, Object> removeMember(@PathVariable String publicId, @PathVariable Long memberId) {
        groupChatService.removeMember(requireUserId(), publicId, memberId);
        return Map.of("ok", true);
    }

    @PostMapping("/groups/{publicId}/admins/{memberId}")
    public Map<String, Object> promoteAdmin(@PathVariable String publicId, @PathVariable Long memberId) {
        return groupChatService.setAdmin(requireUserId(), publicId, memberId, true);
    }

    @DeleteMapping("/groups/{publicId}/admins/{memberId}")
    public Map<String, Object> demoteAdmin(@PathVariable String publicId, @PathVariable Long memberId) {
        return groupChatService.setAdmin(requireUserId(), publicId, memberId, false);
    }

    @PatchMapping("/groups/{publicId}/agents/reply-mode")
    public Map<String, Object> replyMode(@PathVariable String publicId,
                                         @RequestBody GroupChatService.ReplyModeRequest body) {
        return groupChatService.setReplyModes(requireUserId(), publicId, body);
    }

    @PostMapping("/groups/{publicId}/messages")
    public Map<String, Object> send(@PathVariable String publicId,
                                    @RequestBody GroupMessageService.SendRequest body) {
        return groupMessageService.send(requireUserId(), publicId, body);
    }

    @GetMapping("/groups/{publicId}/messages")
    public Object messages(
            @PathVariable String publicId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) String on,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return groupMessageService.listMessages(requireUserId(), publicId, beforeId, on, limit);
    }

    @GetMapping("/groups/{publicId}/messages/days")
    public Object days(
            @PathVariable String publicId,
            @RequestParam(defaultValue = "90") int limit
    ) {
        return groupMessageService.listDays(requireUserId(), publicId, limit);
    }

    @GetMapping("/groups/{publicId}/media")
    public Map<String, Object> media(
            @PathVariable String publicId,
            @RequestParam String kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return groupMessageService.listMedia(requireUserId(), publicId, kind, page, size);
    }

    @PostMapping("/groups/{publicId}/read")
    public Map<String, Object> read(
            @PathVariable String publicId,
            @RequestBody Map<String, Long> body
    ) {
        Long upTo = body != null ? body.get("upToMessageId") : null;
        return groupMessageService.markReadUpTo(requireUserId(), publicId, upTo);
    }

    @GetMapping("/users/search")
    public Map<String, Object> searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return groupChatService.searchUsers(requireUserId(), q, page, size);
    }

    @GetMapping("/groups/{publicId}/agents/{userAgentId}/tasks")
    public Map<String, Object> agentTasks(
            @PathVariable String publicId,
            @PathVariable Long userAgentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return groupChatService.listAgentTasks(requireUserId(), publicId, userAgentId, status, page, size);
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }
}
