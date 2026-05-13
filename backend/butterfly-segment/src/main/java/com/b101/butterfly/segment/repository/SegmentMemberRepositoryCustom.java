package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;

import java.util.List;

/**
 * {@link SegmentMember} bulk INSERT 전용 custom repository.
 *
 * <p>Spring Data 의 {@code saveAll} 은 composite PK 가 채워진 entity 를 "이미 존재 가능성 있음" 으로
 * 판정해 {@code em.merge()} 를 호출 → 1 건마다 SELECT 후 INSERT/UPDATE 로 분기 → batch 적용 X.
 * 빌드 시점은 {@code deleteBySegmentId} 직후라 row 존재 가능성이 0 이므로 SELECT 자체가 무용 →
 * JPA 흐름을 우회하고 raw JDBC batch INSERT 로 처리한다 (S14P31B101-253).
 */
public interface SegmentMemberRepositoryCustom {

    /**
     * 한 statement (또는 batch) 로 N 건 INSERT.
     *
     * @return 적재된 row 수
     */
    int bulkInsert(List<SegmentMember> members);
}
