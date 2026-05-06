package com.b101.butterfly.config.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignExecutionPortAdapter — reporting BC SPI 구현 (api 조립 어댑터)")
class CampaignExecutionPortAdapterTest {

    private EntityManager em;
    private CampaignExecutionPortAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        em = mock(EntityManager.class);
        adapter = new CampaignExecutionPortAdapter();
        Field emField = CampaignExecutionPortAdapter.class.getDeclaredField("em");
        emField.setAccessible(true);
        emField.set(adapter, em);
    }

    /** campaign 모듈의 Channel enum 값을 Mockito로 우회 — 동일 이름이면 변환된다. */
    private enum SourceChannel { PUSH, EMAIL }

    private Object[] sampleRow() {
        return new Object[]{
                10L, 100L, 1L, SourceChannel.PUSH, 50,
                LocalDateTime.parse("2026-01-01T12:00:00")
        };
    }

    @Nested
    @DisplayName("find(executionId)")
    class Find {
        @Test
        @DisplayName("EntityManager 결과를 reporting CampaignExecutionInfo로 변환")
        void mapsRowToInfo() {
            Query query = mock(Query.class);
            given(em.createQuery(org.mockito.ArgumentMatchers.contains("e.campaignExecutionId = :executionId")))
                    .willReturn(query);
            given(query.setParameter("executionId", 10L)).willReturn(query);
            given(query.getSingleResult()).willReturn(sampleRow());

            Optional<CampaignExecutionInfo> result = adapter.find(10L);

            assertThat(result).isPresent();
            CampaignExecutionInfo info = result.orElseThrow();
            assertThat(info.executionId()).isEqualTo(10L);
            assertThat(info.campaignId()).isEqualTo(100L);
            assertThat(info.projectId()).isEqualTo(1L);
            assertThat(info.channel()).isEqualTo(Channel.PUSH);
            assertThat(info.targetCount()).isEqualTo(50);
            assertThat(info.executedAt()).isEqualTo(LocalDateTime.parse("2026-01-01T12:00:00"));
        }

        @Test
        @DisplayName("NoResultException → Optional.empty()")
        void emptyWhenNotFound() {
            Query query = mock(Query.class);
            given(em.createQuery(org.mockito.ArgumentMatchers.contains("e.campaignExecutionId = :executionId")))
                    .willReturn(query);
            given(query.setParameter("executionId", 99L)).willReturn(query);
            given(query.getSingleResult()).willThrow(new NoResultException());

            assertThat(adapter.find(99L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByCampaign(campaignId)")
    class FindByCampaign {
        @Test
        @DisplayName("결과 모두 변환")
        void mapsAll() {
            Query query = mock(Query.class);
            given(em.createQuery(org.mockito.ArgumentMatchers.contains("e.campaignId = :campaignId")))
                    .willReturn(query);
            given(query.setParameter("campaignId", 100L)).willReturn(query);
            // List.of(Object[]) 는 varargs로 해석되어 6개 요소가 되므로 명시 wrap.
            given(query.getResultList()).willReturn(List.<Object[]>of(sampleRow()));

            List<CampaignExecutionInfo> result = adapter.findByCampaign(100L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).campaignId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("결과 없음 → 빈 리스트")
        void emptyList() {
            Query query = mock(Query.class);
            given(em.createQuery(org.mockito.ArgumentMatchers.contains("e.campaignId = :campaignId")))
                    .willReturn(query);
            given(query.setParameter("campaignId", 404L)).willReturn(query);
            given(query.getResultList()).willReturn(List.of());

            assertThat(adapter.findByCampaign(404L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findRecent(since)")
    class FindRecent {
        @Test
        @DisplayName("since 이후 회차 변환")
        void mapsRecent() {
            LocalDateTime since = LocalDateTime.parse("2026-01-01T00:00:00");
            Query query = mock(Query.class);
            given(em.createQuery(org.mockito.ArgumentMatchers.contains(":since")))
                    .willReturn(query);
            given(query.setParameter("since", since)).willReturn(query);
            given(query.getResultList()).willReturn(List.<Object[]>of(sampleRow()));

            List<CampaignExecutionInfo> result = adapter.findRecent(since);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).executedAt()).isAfter(since);
        }
    }

    @Nested
    @DisplayName("targetCount null 안전")
    class NullSafe {
        @Test
        @DisplayName("targetCount null이면 0으로 매핑")
        void nullTargetCountFallsToZero() {
            Object[] nullTarget = new Object[]{
                    1L, 1L, 1L, SourceChannel.EMAIL, null, LocalDateTime.now()
            };
            Query query = mock(Query.class);
            given(em.createQuery(org.mockito.ArgumentMatchers.contains("e.campaignExecutionId = :executionId")))
                    .willReturn(query);
            given(query.setParameter("executionId", 1L)).willReturn(query);
            given(query.getSingleResult()).willReturn(nullTarget);

            CampaignExecutionInfo info = adapter.find(1L).orElseThrow();
            assertThat(info.targetCount()).isZero();
        }
    }
}
