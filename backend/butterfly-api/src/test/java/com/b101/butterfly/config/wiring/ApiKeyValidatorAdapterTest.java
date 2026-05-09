package com.b101.butterfly.config.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.reporting.event.port.ApiKeyInfo;
import com.b101.butterfly.tenant.entity.Project;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ApiKeyValidatorAdapter — reporting/event SPI 구현 (api 조립 어댑터)")
class ApiKeyValidatorAdapterTest {

    /** pk_live_(8) + 24 = 32 — 발급 로직과 동일한 형태의 샘플 평문 키. */
    private static final String VALID_RAW_KEY = "pk_live_0123456789abcdef01234567";

    private ProjectRepository projectRepository;
    private ApiKeyValidatorAdapter adapter;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        adapter = new ApiKeyValidatorAdapter(projectRepository);
    }

    @Nested
    @DisplayName("짧은 회로 가드 — DB 조회 없이 즉시 차단")
    class ShortCircuit {

        @Test
        @DisplayName("null 키")
        void rejectsNull() {
            assertThat(adapter.validate(null)).isEmpty();
            verify(projectRepository, never()).findByPublicApiKey(any());
        }

        @Test
        @DisplayName("길이가 32자가 아니면 차단")
        void rejectsWrongLength() {
            assertThat(adapter.validate("pk_live_short")).isEmpty();
            assertThat(adapter.validate("pk_live_0123456789abcdef0123456789X")).isEmpty(); // 33자
            verify(projectRepository, never()).findByPublicApiKey(any());
        }

        @Test
        @DisplayName("접두사가 pk_live_ 가 아니면 차단")
        void rejectsWrongPrefix() {
            // 32자이지만 접두사 다름
            assertThat(adapter.validate("pk_test_0123456789abcdef01234567")).isEmpty();
            verify(projectRepository, never()).findByPublicApiKey(any());
        }
    }

    @Nested
    @DisplayName("DB 조회 — 평문 키를 SHA-256 해시 후 lookup")
    class DbLookup {

        @Test
        @DisplayName("해시 일치하는 프로젝트가 있으면 ApiKeyInfo(projectId) 반환")
        void returnsProjectIdOnMatch() throws Exception {
            String expectedHash = sha256(VALID_RAW_KEY);
            Project project = projectWithId(42L);
            given(projectRepository.findByPublicApiKey(eq(expectedHash)))
                    .willReturn(Optional.of(project));

            Optional<ApiKeyInfo> result = adapter.validate(VALID_RAW_KEY);

            assertThat(result).contains(new ApiKeyInfo(42L));
        }

        @Test
        @DisplayName("해시 일치 프로젝트 없으면 empty")
        void emptyWhenNoMatch() {
            given(projectRepository.findByPublicApiKey(any())).willReturn(Optional.empty());

            assertThat(adapter.validate(VALID_RAW_KEY)).isEmpty();
        }

        @Test
        @DisplayName("평문이 아닌 해시로 조회 — 평문이 그대로 DB 컬럼과 비교되지 않음")
        void hashesBeforeQuery() throws Exception {
            String expectedHash = sha256(VALID_RAW_KEY);
            given(projectRepository.findByPublicApiKey(eq(expectedHash)))
                    .willReturn(Optional.of(projectWithId(7L)));

            adapter.validate(VALID_RAW_KEY);

            verify(projectRepository).findByPublicApiKey(expectedHash);
            verify(projectRepository, never()).findByPublicApiKey(VALID_RAW_KEY);
        }
    }

    private static Project projectWithId(Long id) throws Exception {
        var ctor = Project.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Project project = ctor.newInstance();
        Field idField = Project.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(project, id);
        return project;
    }

    /** ApiKeyService.sha256() 와 동일한 알고리즘 — 어댑터의 해싱이 이것과 일치하는지 검증용. */
    private static String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
