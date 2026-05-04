package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.repository.SegmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SegmentBuildStaleCron} 단위 테스트 — staleTimeout이 SegmentRepository 호출 시점에 정확히
 * 적용되는지 검증.
 */
@ExtendWith(MockitoExtension.class)
class SegmentBuildStaleCronTest {

    @Mock private SegmentRepository segmentRepository;

    @InjectMocks private SegmentBuildStaleCron cron;

    @Test
    @DisplayName("run — staleTimeout 이전을 임계값으로 markStaleBuildsAsFailed 호출")
    void runUsesConfiguredTimeout() {
        Duration timeout = Duration.ofMinutes(30);
        ReflectionTestUtils.setField(cron, "staleTimeout", timeout);
        when(segmentRepository.markStaleBuildsAsFailed(any(LocalDateTime.class))).thenReturn(2);

        LocalDateTime before = LocalDateTime.now();
        cron.run();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> thresholdCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(segmentRepository).markStaleBuildsAsFailed(thresholdCaptor.capture());
        LocalDateTime threshold = thresholdCaptor.getValue();
        // threshold ≈ now - timeout. before/after 사이에서 측정된 now 기반.
        assertThat(threshold).isBetween(before.minus(timeout), after.minus(timeout));
    }

    @Test
    @DisplayName("run — converted=0이어도 예외 없이 정상 종료")
    void runHandlesZeroConverted() {
        ReflectionTestUtils.setField(cron, "staleTimeout", Duration.ofMinutes(30));
        when(segmentRepository.markStaleBuildsAsFailed(any(LocalDateTime.class))).thenReturn(0);

        cron.run(); // 예외 없이 통과
    }

}
