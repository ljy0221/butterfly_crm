package com.b101.butterfly.tenant.service;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.auth.repository.RefreshTokenRepository;
import com.b101.butterfly.auth.security.ProjectPermissionEvaluator;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.tenant.dto.request.UpdateMemberRoleRequest;
import com.b101.butterfly.tenant.dto.response.ProjectMemberResponse;
import com.b101.butterfly.tenant.exception.TenantErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class ProjectMemberService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProjectPermissionEvaluator projectPermissionEvaluator;

    public List<ProjectMemberResponse> getMembers(Long projectId) {
        return memberRepository.findAllByProjectId(projectId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Transactional
    public void removeMember(Long projectId, Long targetMemberId) {
        Member target = findMemberInProject(projectId, targetMemberId);

        if (target.getRoleType() == RoleType.OWNER) {
            throw new BusinessException(TenantErrorCode.CANNOT_DELETE_OWNER);
        }

        target.softDelete();
        refreshTokenRepository.deleteByMemberId(targetMemberId);
        projectPermissionEvaluator.evictCache(targetMemberId, projectId);
        // TODO(P7): AccessToken 블랙리스트 등록 — JWT 통합 시 구현
        // TODO(P7): AuditRecordedEvent 발행 (MEMBER_REMOVED) — ADR-0009
    }

    @Transactional
    public void updateMemberRole(Long projectId, Long targetMemberId, UpdateMemberRoleRequest request) {
        Member target = findMemberInProject(projectId, targetMemberId);

        if (request.roleType() == RoleType.OWNER) {
            throw new BusinessException(TenantErrorCode.OWNER_TRANSFER_REQUIRED);
        }

        target.updateRole(request.roleType());
        projectPermissionEvaluator.evictCache(targetMemberId, projectId);
        // TODO(P7): AuditRecordedEvent 발행 (MEMBER_ROLE_CHANGED) — ADR-0009
    }

    private Member findMemberInProject(Long projectId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(TenantErrorCode.MEMBER_NOT_IN_PROJECT));

        if (!projectId.equals(member.getProjectId())) {
            throw new BusinessException(TenantErrorCode.MEMBER_NOT_IN_PROJECT);
        }

        return member;
    }
}
