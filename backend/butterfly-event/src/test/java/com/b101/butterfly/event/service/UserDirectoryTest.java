package com.b101.butterfly.event.service;

import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserDirectory userDirectory;

    private static final Long PROJECT_ID = 100L;
    private static final String EXTERNAL_ID = "u_kim_xyz";

    private User user(Long id, String externalId) {
        User u = User.builder()
                .projectId(PROJECT_ID)
                .externalId(externalId)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("lookupOrCreate — 기존 user 있으면 그대로 반환, save 안 함")
    void returnsExistingWhenFound() {
        when(userRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_ID))
                .thenReturn(Optional.of(user(7L, EXTERNAL_ID)));

        User result = userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_ID);

        assertThat(result.getId()).isEqualTo(7L);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("lookupOrCreate — 없으면 신규 생성")
    void createsWhenNotFound() {
        when(userRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_ID))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> {
                    User u = inv.getArgument(0);
                    ReflectionTestUtils.setField(u, "id", 7L);
                    return u;
                });

        User result = userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_ID);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(captor.getValue().getExternalId()).isEqualTo(EXTERNAL_ID);
        assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("lookupOrCreate — null externalUserId는 placeholder로 치환")
    void nullExternalIdMapsToPlaceholder() {
        when(userRepository.findByProjectIdAndExternalId(PROJECT_ID, User.PLACEHOLDER_EXTERNAL_ID))
                .thenReturn(Optional.of(user(9L, User.PLACEHOLDER_EXTERNAL_ID)));

        User result = userDirectory.lookupOrCreate(PROJECT_ID, null);

        verify(userRepository).findByProjectIdAndExternalId(PROJECT_ID, User.PLACEHOLDER_EXTERNAL_ID);
        assertThat(result.isPlaceholder()).isTrue();
    }

    @Test
    @DisplayName("lookupOrCreate — blank externalUserId(공백)도 placeholder로 치환")
    void blankExternalIdMapsToPlaceholder() {
        when(userRepository.findByProjectIdAndExternalId(PROJECT_ID, User.PLACEHOLDER_EXTERNAL_ID))
                .thenReturn(Optional.of(user(9L, User.PLACEHOLDER_EXTERNAL_ID)));

        userDirectory.lookupOrCreate(PROJECT_ID, "   ");

        verify(userRepository).findByProjectIdAndExternalId(PROJECT_ID, User.PLACEHOLDER_EXTERNAL_ID);
    }
}
