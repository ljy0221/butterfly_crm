package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.UserTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTagRepository extends JpaRepository<UserTag, Long> {

    /**
     * 한 프로젝트의 여러 사용자의 모든 tag 를 한 번에 조회. 캠페인 fan-out 시
     * userId 별 변수 map 을 만드는 진입 쿼리.
     */
    List<UserTag> findAllByProjectIdAndUserIdIn(Long projectId, Collection<Long> userIds);
}
