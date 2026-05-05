package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.dto.response.SegmentUserResponse;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.UserSummary;
import com.b101.butterfly.segment.port.UserSummaryReader;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 8-1 ([-119]) — 세그먼트 대상 유저 목록 API #43.
 *
 * <p>흐름:
 * <ol>
 *   <li>segment 조회 + projectId 일치 검증 (불일치 시 404 — 정보 노출 회피)</li>
 *   <li>{@link SegmentMemberRepository#findActiveBySegmentId} 페이지 조회 (만료 row 자동 제외)</li>
 *   <li>{@link UserSummaryReader#findByUserIds}로 user 요약 일괄 조회 (project 경계 + placeholder 제외)</li>
 *   <li>{@link SegmentUserResponse} 매핑 — 미존재 user는 userId/matchedAt만 채우고 나머지 null</li>
 * </ol>
 *
 * <p>Codex 합의 반영:
 * <ul>
 *   <li>다른 프로젝트 segment 접근 시 403이 아닌 404 — segment 존재 자체를 노출하지 않음</li>
 *   <li>만료 row(expires_at &le; now)는 repository 단계에서 제외 — 만료 배치 cron 의존 X</li>
 *   <li>userIds 일괄 IN 쿼리 (N+1 회피)</li>
 *   <li>v1 known-user only — anonymous placeholder는 어댑터에서 제외</li>
 *   <li>{@code lastActiveAt}은 v1에서 항상 null (ERD에 last_active_at 컬럼 없음)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SegmentUsersService {

    private final SegmentRepository segmentRepository;
    private final SegmentMemberRepository segmentMemberRepository;
    private final UserSummaryReader userSummaryReader;

    public PageResponse<SegmentUserResponse> listUsers(
            Long projectId, Long segmentId, Pageable pageable) {
        Segment segment =
                segmentRepository
                        .findById(segmentId)
                        .orElseThrow(
                                () -> new BusinessException(SegmentErrorCode.SEGMENT_404_NOT_FOUND));
        if (!Objects.equals(segment.getProjectId(), projectId)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_404_NOT_FOUND);
        }

        Page<SegmentMember> page =
                segmentMemberRepository.findActiveBySegmentId(
                        segmentId, LocalDateTime.now(), pageable);

        List<Long> userIds = page.getContent().stream().map(SegmentMember::getUserId).toList();
        Map<Long, UserSummary> summaries = userSummaryReader.findByUserIds(projectId, userIds);

        List<SegmentUserResponse> content =
                page.getContent().stream()
                        .map(
                                member -> {
                                    UserSummary s = summaries.get(member.getUserId());
                                    return new SegmentUserResponse(
                                            member.getUserId(),
                                            s != null ? s.externalId() : null,
                                            s != null ? s.email() : null,
                                            null, // lastActiveAt v1 미구현
                                            member.getMatchedAt());
                                })
                        .toList();

        return PageResponse.of(
                content, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
