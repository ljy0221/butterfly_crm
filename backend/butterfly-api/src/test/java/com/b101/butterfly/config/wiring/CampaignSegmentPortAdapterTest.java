package com.b101.butterfly.config.wiring;

import com.b101.butterfly.reporting.report.port.CampaignSegmentInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("CampaignSegmentPortAdapter — reporting BC SPI 구현 (api 조립 어댑터)")
class CampaignSegmentPortAdapterTest {

    private EntityManager em;
    private CampaignSegmentPortAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        em = mock(EntityManager.class);
        adapter = new CampaignSegmentPortAdapter();
        Field emField = CampaignSegmentPortAdapter.class.getDeclaredField("em");
        emField.setAccessible(true);
        emField.set(adapter, em);
    }

    @Test
    @DisplayName("빈 입력 → EntityManager 호출하지 않고 빈 맵 반환")
    void emptyInputShortCircuits() {
        Map<Long, CampaignSegmentInfo> result = adapter.findByCampaignIds(List.of());
        assertThat(result).isEmpty();
        verify(em, never()).createQuery(ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("결과를 campaignId → CampaignSegmentInfo 맵으로 변환")
    void mapsRowsToInfoMap() {
        Query query = mock(Query.class);
        given(em.createQuery(ArgumentMatchers.contains("c.segmentId"))).willReturn(query);
        given(query.setParameter("campaignIds", List.of(100L, 101L))).willReturn(query);
        given(query.getResultList()).willReturn(List.<Object[]>of(
                new Object[]{100L, 10L, "VIP"},
                new Object[]{101L, 20L, "NEW"}
        ));

        Map<Long, CampaignSegmentInfo> result = adapter.findByCampaignIds(List.of(100L, 101L));

        assertThat(result).hasSize(2);
        assertThat(result.get(100L).segmentId()).isEqualTo(10L);
        assertThat(result.get(100L).segmentName()).isEqualTo("VIP");
        assertThat(result.get(101L).segmentId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("LEFT JOIN으로 segment_id NULL 캠페인도 결과에 포함 (segment 정보는 null)")
    void includesUnsegmentedCampaigns() {
        Query query = mock(Query.class);
        given(em.createQuery(ArgumentMatchers.contains("c.segmentId"))).willReturn(query);
        given(query.setParameter("campaignIds", List.of(200L))).willReturn(query);
        given(query.getResultList()).willReturn(List.<Object[]>of(
                new Object[]{200L, null, null}
        ));

        Map<Long, CampaignSegmentInfo> result = adapter.findByCampaignIds(List.of(200L));

        assertThat(result).hasSize(1);
        assertThat(result.get(200L).segmentId()).isNull();
        assertThat(result.get(200L).segmentName()).isNull();
    }
}
