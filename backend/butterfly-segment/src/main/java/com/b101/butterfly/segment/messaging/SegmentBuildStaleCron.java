package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Stale 빌드 복구 cron — heartbeat가 timeout 이상 갱신되지 않은 BUILDING segment를 FAILED로 일괄
 * 전환. worker 크래시·OOM·partition rebalance 등으로 멈춘 빌드를 정리해 다음 rebuild 요청이 시작
 * 가능하도록 한다.
 *
 * <p>{@code SegmentBuildJob}의 stale 인수 timeout과 함께 두 단계 안전망:
 * <ul>
 *   <li>새 rebuild 요청이 stale BUILDING을 인수 (즉시 복구)
 *   <li>요청 없어도 cron이 주기적으로 FAILED 마킹 (운영 가시성 ↑)
 * </ul>
 *
 * <p>설정 (application.yml):
 * <pre>
 * segment.build.stale-timeout=PT30M       # ISO-8601 Duration, 기본 30분
 * segment.build.stale-cron=0 *&#47;5 * * * * # 매 5분마다, 기본값
 * </pre>
 */
@Slf4j
@Component
@Profile("worker")
@EnableScheduling
@RequiredArgsConstructor
public class SegmentBuildStaleCron {

    private final SegmentRepository segmentRepository;

    @Value("${segment.build.stale-timeout:PT30M}")
    private Duration staleTimeout;

    @Scheduled(cron = "${segment.build.stale-cron:0 */5 * * * *}", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        LocalDateTime threshold = LocalDateTime.now().minus(staleTimeout);
        int converted = segmentRepository.markStaleBuildsAsFailed(threshold);
        if (converted > 0) {
            log.warn(
                    "Stale BUILDING segment {}건 FAILED 전환 (heartbeat < {}, timeout={})",
                    converted,
                    threshold,
                    staleTimeout);
        }
    }
}
