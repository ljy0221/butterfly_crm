package com.b101.butterfly.event.port;

import com.b101.butterfly.event.entity.UserTag;
import com.b101.butterfly.event.repository.UserTagRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserTagLookupPort} 의 JPA 기반 실 구현. UserTag entity 의 일괄 조회 결과를
 * userId 별 (tagKey → tagValue) 맵으로 그룹화한다.
 */
@Component
@RequiredArgsConstructor
public class UserTagLookupAdapter implements UserTagLookupPort {

    private final UserTagRepository userTagRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Map<String, String>> getTagsByUserIds(Long projectId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userTagRepository.findAllByProjectIdAndUserIdIn(projectId, userIds).stream()
                .collect(Collectors.groupingBy(
                        UserTag::getUserId,
                        Collectors.toMap(UserTag::getTagKey, UserTag::getTagValue)
                ));
    }
}
