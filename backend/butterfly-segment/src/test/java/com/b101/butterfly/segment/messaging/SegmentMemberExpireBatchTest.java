package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentMemberExpireBatchTest {

    @Mock private SegmentMemberRepository segmentMemberRepository;

    @InjectMocks private SegmentMemberExpireBatch batch;

    @Test
    @DisplayName("expireOldMembers — 현재 KST 시각으로 deleteExpiredMembers 호출")
    void invokesRepositoryWithKstNow() {
        when(segmentMemberRepository.deleteExpiredMembers(org.mockito.ArgumentMatchers.any()))
                .thenReturn(3);

        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        batch.expireOldMembers();
        LocalDateTime after = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(segmentMemberRepository).deleteExpiredMembers(nowCaptor.capture());
        LocalDateTime captured = nowCaptor.getValue();
        assertThat(captured).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }
}
