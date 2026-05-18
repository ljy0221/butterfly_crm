package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.port.SegmentVariableSnapshotWriter;

public class StubSegmentVariableSnapshotWriter implements SegmentVariableSnapshotWriter {

    @Override
    public void refresh(Long projectId, Long segmentId, GroupNode conditionTree) {
        // event 모듈 미합류 환경에서는 스냅샷할 원천 데이터가 없으므로 no-op.
    }
}
