package com.b101.butterfly.segment.port;

import com.b101.butterfly.segment.domain.condition.GroupNode;

/**
 * 세그먼트 빌드 결과 대상자별 개인화 변수 스냅샷을 갱신한다.
 *
 * <p>segment 모듈은 conditionTree만 알고 실제 값 저장소(events/user_tags 등)는 event 모듈이
 * 소유하므로 포트로 분리한다. 구현체가 없으면 no-op stub 이 동작한다.
 */
public interface SegmentVariableSnapshotWriter {

    void refresh(Long projectId, Long segmentId, GroupNode conditionTree);
}
