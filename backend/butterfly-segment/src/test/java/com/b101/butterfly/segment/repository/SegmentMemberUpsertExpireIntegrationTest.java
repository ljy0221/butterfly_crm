package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.entity.SegmentMemberId;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6-3 핵심 SQL — UPSERT + 만료 배치 — 실제 MySQL에서 검증.
 *
 * <p>Mockito 단위 테스트는 호출/파라미터만 검사 가능 — ON DUPLICATE KEY UPDATE의 GREATEST +
 * Cumulative win 의미와 DELETE 조건은 실제 DB에서만 확인 가능하다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentMemberUpsertExpireIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentMemberRepository repository;
    @Autowired private EntityManager entityManager;

    private static final Long SEGMENT_ID = 100L;
    private static final Long USER_ID = 42L;

    @Nested
    @DisplayName("upsertMember — Codex 합의 D3 expires_at 정책")
    class UpsertTests {

        @Test
        @DisplayName("최초 INSERT — expires_at 그대로 저장")
        void firstInsert() {
            LocalDateTime expiry = LocalDateTime.of(2026, 6, 1, 0, 0);
            repository.upsertMember(SEGMENT_ID, USER_ID, LocalDateTime.now(), expiry);

            entityManager.clear();
            SegmentMember saved = find(SEGMENT_ID, USER_ID).orElseThrow();
            assertThat(saved.getExpiresAt()).isEqualTo(expiry);
        }

        @Test
        @DisplayName("두 번째 UPSERT — 기존보다 늦은 expires_at이면 갱신 (GREATEST)")
        void secondUpsertExtendsExpiry() {
            LocalDateTime first = LocalDateTime.of(2026, 6, 1, 0, 0);
            LocalDateTime later = LocalDateTime.of(2026, 7, 1, 0, 0);

            repository.upsertMember(SEGMENT_ID, USER_ID, LocalDateTime.now(), first);
            repository.upsertMember(SEGMENT_ID, USER_ID, LocalDateTime.now(), later);

            entityManager.clear();
            assertThat(find(SEGMENT_ID, USER_ID).orElseThrow().getExpiresAt()).isEqualTo(later);
        }

        @Test
        @DisplayName("두 번째 UPSERT — 기존보다 이른 expires_at이면 유지 (GREATEST)")
        void secondUpsertKeepsLaterExpiry() {
            LocalDateTime later = LocalDateTime.of(2026, 7, 1, 0, 0);
            LocalDateTime earlier = LocalDateTime.of(2026, 6, 1, 0, 0);

            repository.upsertMember(SEGMENT_ID, USER_ID, LocalDateTime.now(), later);
            repository.upsertMember(SEGMENT_ID, USER_ID, LocalDateTime.now(), earlier);

            entityManager.clear();
            assertThat(find(SEGMENT_ID, USER_ID).orElseThrow().getExpiresAt()).isEqualTo(later);
        }

        @Test
        @DisplayName("기존 NULL (cumulative) → 새 not-null이 와도 NULL 유지 (cumulative win)")
        void cumulativeWinStaysNull() {
            repository.upsertMember(SEGMENT_ID, USER_ID, LocalDateTime.now(), null);
            repository.upsertMember(
                    SEGMENT_ID, USER_ID, LocalDateTime.now(),
                    LocalDateTime.of(2026, 6, 1, 0, 0));

            entityManager.clear();
            assertThat(find(SEGMENT_ID, USER_ID).orElseThrow().getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("기존 not-null → 새 NULL이 오면 NULL로 전환 (cumulative win)")
        void newNullOverridesExisting() {
            repository.upsertMember(
                    SEGMENT_ID, USER_ID, LocalDateTime.now(),
                    LocalDateTime.of(2026, 6, 1, 0, 0));
            repository.upsertMember(SEGMENT_ID, USER_ID, LocalDateTime.now(), null);

            entityManager.clear();
            assertThat(find(SEGMENT_ID, USER_ID).orElseThrow().getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("matched_at은 매 UPSERT마다 갱신")
        void matchedAtAlwaysUpdates() {
            LocalDateTime first = LocalDateTime.of(2026, 5, 1, 10, 0);
            LocalDateTime second = LocalDateTime.of(2026, 5, 4, 10, 0);

            repository.upsertMember(SEGMENT_ID, USER_ID, first, null);
            repository.upsertMember(SEGMENT_ID, USER_ID, second, null);

            entityManager.clear();
            assertThat(find(SEGMENT_ID, USER_ID).orElseThrow().getMatchedAt()).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("deleteExpiredMembers — KST 03:00 cron 동작 검증")
    class ExpireTests {

        @Test
        @DisplayName("expires_at <= now만 삭제, > now 또는 NULL은 보존")
        void deletesOnlyExpired() {
            LocalDateTime now = LocalDateTime.of(2026, 5, 4, 3, 0);

            // 만료 (삭제 대상)
            repository.upsertMember(1L, 11L, now.minusDays(10), now.minusDays(1));
            repository.upsertMember(1L, 12L, now.minusDays(10), now); // 경계 — 삭제 대상

            // 미만료 (보존)
            repository.upsertMember(1L, 13L, now.minusDays(1), now.plusDays(1));

            // cumulative — 보존
            repository.upsertMember(2L, 21L, now.minusDays(100), null);

            entityManager.flush();
            entityManager.clear();

            int deleted = repository.deleteExpiredMembers(now);
            assertThat(deleted).isEqualTo(2);

            entityManager.clear();
            assertThat(find(1L, 11L)).isEmpty();
            assertThat(find(1L, 12L)).isEmpty();
            assertThat(find(1L, 13L)).isPresent();
            assertThat(find(2L, 21L)).isPresent();
        }

        @Test
        @DisplayName("만료 row 0건이면 삭제 0건")
        void noExpiredReturnsZero() {
            LocalDateTime now = LocalDateTime.of(2026, 5, 4, 3, 0);
            repository.upsertMember(1L, 11L, now, now.plusDays(7));
            entityManager.flush();

            int deleted = repository.deleteExpiredMembers(now);
            assertThat(deleted).isZero();
        }
    }

    private Optional<SegmentMember> find(Long segmentId, Long userId) {
        return repository.findById(new SegmentMemberId(segmentId, userId));
    }
}
