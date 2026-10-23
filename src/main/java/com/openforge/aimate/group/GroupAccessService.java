package com.openforge.aimate.group;

import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GroupAccessService {

    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;

    public ChatGroup requireActiveGroup(String publicId) {
        ChatGroup g = groupRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "群组不存在"));
        if (g.getStatus() != ChatGroup.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "群组已解散");
        }
        return g;
    }

    public GroupMember requireUserMember(ChatGroup group, Long userId) {
        return memberRepository.findByGroupIdAndUserId(group.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "你不是该群成员"));
    }

    public void requireOwnerOrAdmin(GroupMember m) {
        if (m.getRole() != GroupMember.Role.OWNER && m.getRole() != GroupMember.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要群主或管理员权限");
        }
    }

    public void requireOwner(GroupMember m) {
        if (m.getRole() != GroupMember.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要群主权限");
        }
    }
}
