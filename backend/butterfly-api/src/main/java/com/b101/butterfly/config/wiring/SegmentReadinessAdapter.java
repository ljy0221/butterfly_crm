package com.b101.butterfly.config.wiring;

import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.lifecycle.port.SegmentBuildStatusView;
import com.b101.butterfly.campaign.lifecycle.port.SegmentReadinessPort;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.api.SegmentLookupPort;
import com.b101.butterfly.segment.api.SegmentSnapshot;
import com.b101.butterfly.segment.entity.BuildStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * campaign 측 SegmentReadinessPort의 구현체. SegmentLookupPort에 위임한다.
 *
 * <p>BC ACL — campaign 도메인은 SegmentSnapshot/BuildStatus를 직접 알지 않고,
 * 본 어댑터에서만 segment 모듈 타입을 campaign port 타입으로 변환한다.
 *
 * <p>본 클래스를 campaign 모듈에 두지 않고 api 모듈에 둔 이유:
 * campaign → segment.entity 직접 의존을 ArchUnit이 차단하므로 조립(wiring) 레이어에 위치시킨다.
 */
@Component
@RequiredArgsConstructor
public class SegmentReadinessAdapter implements SegmentReadinessPort {

    private final SegmentLookupPort segmentLookupPort;

    @Override
    public void requireReady(Long segmentId) {
        SegmentSnapshot snapshot = segmentLookupPort.findById(segmentId)
                .orElseThrow(() -> new BusinessException(CampaignErrorCode.SEGMENT_NOT_FOUND));

        if (snapshot.buildStatus() != BuildStatus.READY) {
            throw new BusinessException(
                    CampaignErrorCode.SEGMENT_NOT_READY,
                    "세그먼트가 발송 가능한 상태가 아닙니다. 현재 상태: " + snapshot.buildStatus()
            );
        }
    }

    @Override
    public Optional<SegmentBuildStatusView> findStatus(Long segmentId) {
        return segmentLookupPort.findById(segmentId)
                .map(s -> new SegmentBuildStatusView(s.segmentId(), s.buildStatus().name()));
    }
}
