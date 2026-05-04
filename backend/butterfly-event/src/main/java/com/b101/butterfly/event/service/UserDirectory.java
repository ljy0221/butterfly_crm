package com.b101.butterfly.event.service;

import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserDirectory {

    private final UserRepository userRepository;

    @Transactional
    public User lookupOrCreate(Long projectId, String externalUserId) {
        String key = (externalUserId != null && !externalUserId.isBlank())
                ? externalUserId
                : User.PLACEHOLDER_EXTERNAL_ID;

        return userRepository.findByProjectIdAndExternalId(projectId, key)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .projectId(projectId)
                                .externalId(key)
                                .build()
                ));
    }
}
