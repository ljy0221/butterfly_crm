package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매일 KST 03:00 — 만료된 segment_members 일괄 삭제 cron. Phase 6-3 Codex 합의 D4.
 *
 * <p>cumulative 매칭 (`expires_at IS NULL`)은 자동 제외 (영구 매칭 의미).
 * Relative/Absolute 매칭만 삭제 대상이 된다.
 *
 * <p>{@code segments.estimated_count}는 갱신하지 않는다 (마지막 빌드 시점 기준 값으로 보존,
 * drift 허용 — 다음 rebuild에서 재계산).
 *
 * <p>cron 표현은 configurable: {@code segment.member.expire-cron} (기본 매일 03:00 Asia/Seoul).
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class SegmentMemberExpireBatch {

    private final SegmentMemberRepository segmentMemberRepository;

    @Scheduled(
            cron = "${segment.member.expire-cron:0 0 3 * * *}",
            zone = "Asia/Seoul")
    @Transactional
    public void expireOldMembers() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        int deleted = segmentMemberRepository.deleteExpiredMembers(now);
        log.info("Segment member 만료 배치 — deleted={} now={}", deleted, now);
    }
}
