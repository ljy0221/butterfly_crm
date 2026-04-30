package com.b101.butterfly.segment.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 외부 모듈(campaign 등)이 segment 정보를 ID로 조회할 때 사용하는 inbound 포트.
 *
 * <p>P4 (모듈러 모놀리스 전환 - port SPI 확대). Campaign이 segmentId를 Long으로 보유한 뒤 표시·필터에
 * segment 이름·상태가 필요할 때 본 포트로 조회한다. {@code findAllByIds}는 N+1 방지를 위해 한 번에 fetch.
 *
 * <p>구현체는 segment 모듈 내부에 있으며 외부 모듈은 본 인터페이스만 의존한다.
 */
public interface SegmentLookupPort {

    Optional<SegmentSnapshot> findById(Long segmentId);

    List<SegmentSnapshot> findAllByIds(Collection<Long> segmentIds);
}
