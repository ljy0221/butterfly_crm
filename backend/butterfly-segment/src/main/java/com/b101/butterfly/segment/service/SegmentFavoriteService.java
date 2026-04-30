package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.dto.response.FavoritesResponse;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.entity.SegmentFavorite;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.repository.SegmentFavoriteRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 즐겨찾기 등록/해제 + 목록 조회.
 *
 * <p>등록/해제는 둘 다 멱등 — 이미 있는 상태로 add 호출하거나 없는 상태로 remove 호출해도 예외 없이 no-op.
 *
 * <p>목록 조회({@link #list})는 회원의 모든 즐겨찾기 중 현재 프로젝트({@code projectId}) 소속만
 * favoritedAt 내림차순으로 반환한다. {@code recentlyUsed}는 campaign 모듈 합류 후 채움 — 현재는 빈 리스트.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SegmentFavoriteService {

    private final SegmentRepository segmentRepository;
    private final SegmentFavoriteRepository segmentFavoriteRepository;

    public void addFavorite(Long projectId, Long segmentId, Long memberId) {
        ensureSegmentExists(segmentId);
        if (segmentFavoriteRepository.existsBySegmentIdAndMemberId(segmentId, memberId)) {
            return;
        }
        segmentFavoriteRepository.save(
                SegmentFavorite.builder().segmentId(segmentId).memberId(memberId).build());
    }

    public void removeFavorite(Long projectId, Long segmentId, Long memberId) {
        ensureSegmentExists(segmentId);
        if (!segmentFavoriteRepository.existsBySegmentIdAndMemberId(segmentId, memberId)) {
            return;
        }
        segmentFavoriteRepository.deleteBySegmentIdAndMemberId(segmentId, memberId);
    }

    /**
     * 회원의 즐겨찾기를 현재 프로젝트로 필터링해 favoritedAt 내림차순으로 반환.
     *
     * <p>{@code SegmentFavorite}는 프로젝트 단위가 아니라 회원 단위라 다른 프로젝트의 segment도 들어 있을 수
     * 있다 → 본 메서드에서 {@code projectId}로 후필터링한다. {@link Segment}는
     * {@code @SQLRestriction("is_deleted = false")}으로 soft-deleted 행을 자동 제외한다.
     *
     * <p>{@code recentlyUsed}는 campaign 사용 시각 기반 — campaign 모듈 합류 후 채움 (Phase 7).
     */
    @Transactional(readOnly = true)
    public FavoritesResponse list(Long projectId, Long memberId) {
        List<SegmentFavorite> favoriteRows = segmentFavoriteRepository.findByMemberId(memberId);
        if (favoriteRows.isEmpty()) {
            return new FavoritesResponse(List.of(), List.of());
        }

        Map<Long, LocalDateTime> favoritedAtBySegmentId =
                favoriteRows.stream()
                        .collect(
                                Collectors.toMap(
                                        SegmentFavorite::getSegmentId,
                                        SegmentFavorite::getCreatedAt));

        List<Segment> activeSegments =
                segmentRepository.findAllById(favoritedAtBySegmentId.keySet()).stream()
                        .filter(s -> projectId.equals(s.getProjectId()))
                        .toList();

        List<FavoritesResponse.FavoriteItem> favorites =
                activeSegments.stream()
                        .sorted(
                                Comparator.comparing(
                                                (Segment s) ->
                                                        favoritedAtBySegmentId.get(s.getId()))
                                        .reversed())
                        .map(
                                s ->
                                        new FavoritesResponse.FavoriteItem(
                                                s.getId(),
                                                s.getName(),
                                                s.getEstimatedCount(),
                                                favoritedAtBySegmentId.get(s.getId())))
                        .toList();

        // TODO Phase 7 — campaign 모듈 합류 시 campaigns.last_used_at 기준으로 채움
        List<FavoritesResponse.RecentlyUsedItem> recentlyUsed = List.of();

        return new FavoritesResponse(favorites, recentlyUsed);
    }

    private void ensureSegmentExists(Long segmentId) {
        if (!segmentRepository.existsById(segmentId)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_404_NOT_FOUND);
        }
    }
}
