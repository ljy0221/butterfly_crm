package com.b101.butterfly.campaign.lifecycle.infrastructure.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.segment.port.LinkedCampaignsView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LinkedCampaignCheckerImpl")
class LinkedCampaignCheckerImplTest {

    private static final Long SEGMENT_ID = 42L;

    private CampaignRepository campaignRepository;
    private LinkedCampaignCheckerImpl checker;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        checker = new LinkedCampaignCheckerImpl(campaignRepository);
    }

    @Test
    @DisplayName("hasActiveOrSending — ACTIVE/PAUSED 캠페인이 있으면 true")
    void hasActiveOrSending_true() {
        given(campaignRepository.existsBySegmentIdAndCampaignStatusIn(
                        eq(SEGMENT_ID),
                        eq(List.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED))))
                .willReturn(true);

        assertThat(checker.hasActiveOrSending(SEGMENT_ID)).isTrue();
    }

    @Test
    @DisplayName("hasActiveOrSending — 차단 status 캠페인이 없으면 false")
    void hasActiveOrSending_false() {
        given(campaignRepository.existsBySegmentIdAndCampaignStatusIn(eq(SEGMENT_ID), any()))
                .willReturn(false);

        assertThat(checker.hasActiveOrSending(SEGMENT_ID)).isFalse();
    }

    @Test
    @DisplayName("hasActiveOrSending — segmentId null 이면 false, Repository 호출 없음")
    void hasActiveOrSending_nullSegmentId() {
        assertThat(checker.hasActiveOrSending(null)).isFalse();
        verifyNoInteractions(campaignRepository);
    }

    @Test
    @DisplayName("listLinkedCampaigns — status 별로 정확히 그룹화")
    void listLinkedCampaigns_grouping() {
        Campaign active1 = mockCampaign(1L, "active-1", CampaignStatus.ACTIVE);
        Campaign active2 = mockCampaign(2L, "active-2", CampaignStatus.ACTIVE);
        Campaign paused = mockCampaign(3L, "paused-1", CampaignStatus.PAUSED);
        Campaign draft = mockCampaign(4L, "draft-1", CampaignStatus.DRAFT);

        given(campaignRepository.findAllBySegmentId(SEGMENT_ID))
                .willReturn(List.of(active1, active2, paused, draft));

        LinkedCampaignsView view = checker.listLinkedCampaigns(SEGMENT_ID);

        assertThat(view.active()).hasSize(2).extracting("campaignId").containsExactly(1L, 2L);
        assertThat(view.paused()).hasSize(1).extracting("campaignId").containsExactly(3L);
        assertThat(view.draft()).hasSize(1).extracting("campaignId").containsExactly(4L);
        assertThat(view.ended()).isEmpty();
        assertThat(view.totalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("listLinkedCampaigns — 캠페인이 없으면 모든 그룹 empty")
    void listLinkedCampaigns_empty() {
        given(campaignRepository.findAllBySegmentId(SEGMENT_ID)).willReturn(List.of());

        LinkedCampaignsView view = checker.listLinkedCampaigns(SEGMENT_ID);

        assertThat(view.totalCount()).isZero();
        assertThat(view.active()).isEmpty();
        assertThat(view.paused()).isEmpty();
        assertThat(view.draft()).isEmpty();
        assertThat(view.ended()).isEmpty();
    }

    @Test
    @DisplayName("listLinkedCampaigns — segmentId null 이면 empty view, Repository 호출 없음")
    void listLinkedCampaigns_nullSegmentId() {
        LinkedCampaignsView view = checker.listLinkedCampaigns(null);

        assertThat(view.totalCount()).isZero();
        verifyNoInteractions(campaignRepository);
    }

    @Test
    @DisplayName("listLinkedCampaigns — summary 의 campaignStatus 는 enum.name() 문자열")
    void listLinkedCampaigns_statusAsString() {
        Campaign ended = mockCampaign(99L, "ended-1", CampaignStatus.ENDED);
        given(campaignRepository.findAllBySegmentId(SEGMENT_ID)).willReturn(List.of(ended));

        LinkedCampaignsView view = checker.listLinkedCampaigns(SEGMENT_ID);

        assertThat(view.ended()).hasSize(1);
        assertThat(view.ended().get(0).campaignStatus()).isEqualTo("ENDED");
    }

    private static Campaign mockCampaign(Long id, String name, CampaignStatus status) {
        Campaign c = mock(Campaign.class);
        given(c.getCampaignId()).willReturn(id);
        given(c.getCampaignName()).willReturn(name);
        given(c.getCampaignStatus()).willReturn(status);
        return c;
    }
}
