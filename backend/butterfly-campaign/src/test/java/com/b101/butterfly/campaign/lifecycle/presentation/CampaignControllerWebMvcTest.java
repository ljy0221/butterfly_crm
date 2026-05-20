package com.b101.butterfly.campaign.lifecycle.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b101.butterfly.campaign.lifecycle.application.CampaignService;
import com.b101.butterfly.campaign.lifecycle.application.result.CampaignDetailResult;
import com.b101.butterfly.campaign.lifecycle.application.result.CampaignListResult;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignType;
import com.b101.butterfly.campaign.lifecycle.domain.enums.SendMode;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CampaignController.class)
@ActiveProfiles("api")
@DisplayName("CampaignController WebMvcTest")
class CampaignControllerWebMvcTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long CAMPAIGN_ID = 100L;
    private static final Long MEMBER_ID = 1L;
    private static final String BASE_URL = "/api/v1/projects/" + PROJECT_ID + "/campaigns";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CampaignService campaignService;

    /**
     * 컨트롤러가 {@link org.springframework.security.core.Authentication} 파라미터로 memberId를 추출하므로
     * SecurityContext에 인증 정보를 미리 주입한다. 토큰 username = String.valueOf(memberId).
     */
    @BeforeEach
    void setUpSecurityContext() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_EDITOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /campaigns — 캠페인 생성")
    class CreateCampaign {

        @Test
        @DisplayName("정상 요청 → 201 + body campaignId")
        void createHappy() throws Exception {
            given(campaignService.createCampaign(any())).willReturn(CAMPAIGN_ID);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "messageTemplateId": 10,
                                        "campaignName": "테스트 캠페인",
                                        "campaignDescription": "설명"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.campaignId").value(CAMPAIGN_ID));

            verify(campaignService).createCampaign(any());
        }

        @Test
        @DisplayName("campaignName blank → 400")
        void createNameBlank() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "messageTemplateId": 10,
                                        "campaignName": "",
                                        "campaignDescription": "설명"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("messageTemplateId null → 400")
        void createTemplateIdNull() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "campaignName": "이름",
                                        "campaignDescription": "설명"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /campaigns/{id} — 캠페인 단건 조회")
    class GetCampaign {

        @Test
        @DisplayName("정상 요청 → 200 + body")
        void getHappy() throws Exception {
            CampaignDetailResult result = new CampaignDetailResult(
                    CAMPAIGN_ID, PROJECT_ID, 10L, null, 5L,
                    "캠페인명", "설명",
                    CampaignType.ONE_TIME, SendMode.IMMEDIATE, Channel.PUSH,
                    CampaignStatus.DRAFT,
                    LocalDateTime.of(2026, 1, 1, 0, 0),
                    LocalDateTime.of(2026, 1, 2, 0, 0),
                    null
            );
            given(campaignService.getCampaign(PROJECT_ID, CAMPAIGN_ID)).willReturn(result);

            mockMvc.perform(get(BASE_URL + "/" + CAMPAIGN_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.campaignId").value(CAMPAIGN_ID))
                    .andExpect(jsonPath("$.data.campaignName").value("캠페인명"));
        }
    }

    @Nested
    @DisplayName("PATCH /campaigns/{id} — 캠페인 수정")
    class UpdateCampaign {

        @Test
        @DisplayName("정상 요청 → 200 + body campaignId")
        void updateHappy() throws Exception {
            given(campaignService.updateCampaign(any())).willReturn(CAMPAIGN_ID);

            mockMvc.perform(patch(BASE_URL + "/" + CAMPAIGN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "campaignName": "수정된 이름",
                                        "campaignDescription": "수정된 설명",
                                        "messageTemplateId": 10,
                                        "segmentId": 42
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.campaignId").value(CAMPAIGN_ID));
        }
    }

    @Nested
    @DisplayName("PATCH /campaigns/{id}/status — 상태 전이")
    class TransitionStatus {

        @Test
        @DisplayName("정상 요청 → 200 + 새 status")
        void transitionHappy() throws Exception {
            given(campaignService.transitionStatus(any())).willReturn(CampaignStatus.ACTIVE);

            mockMvc.perform(patch(BASE_URL + "/" + CAMPAIGN_ID + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "status": "ACTIVE"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.campaignId").value(CAMPAIGN_ID))
                    .andExpect(jsonPath("$.data.campaignStatus").value("ACTIVE"));
        }

        @Test
        @DisplayName("status null → 400")
        void transitionStatusNull() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/" + CAMPAIGN_ID + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /campaigns/{id} — 캠페인 삭제")
    class DeleteCampaign {

        @Test
        @DisplayName("정상 요청 → 200 + ApiResponse.ok()")
        void deleteHappy() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/" + CAMPAIGN_ID))
                    .andExpect(status().isOk());

            verify(campaignService).deleteCampaign(CAMPAIGN_ID, PROJECT_ID);
        }
    }

    @Nested
    @DisplayName("POST /campaigns/{id}/duplicate — 캠페인 재실행(새 초안 복사)")
    class DuplicateCampaign {

        @Test
        @DisplayName("정상 요청 → 201 + 새 campaignId")
        void duplicateHappy() throws Exception {
            Long newId = 999L;
            given(campaignService.duplicateCampaign(CAMPAIGN_ID, PROJECT_ID, MEMBER_ID)).willReturn(newId);

            mockMvc.perform(post(BASE_URL + "/" + CAMPAIGN_ID + "/duplicate"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.campaignId").value(newId));

            verify(campaignService).duplicateCampaign(CAMPAIGN_ID, PROJECT_ID, MEMBER_ID);
        }
    }

    @Nested
    @DisplayName("GET /campaigns — 캠페인 목록")
    class GetCampaignList {

        @Test
        @DisplayName("정상 요청 → 200 + 페이지 응답")
        void listHappy() throws Exception {
            CampaignListResult item = new CampaignListResult(
                    CAMPAIGN_ID, "캠페인명", Channel.PUSH, CampaignStatus.DRAFT,
                    LocalDateTime.of(2026, 1, 1, 0, 0)
            );
            given(campaignService.getCampaignList(any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].campaignId").value(CAMPAIGN_ID));
        }

        @Test
        @DisplayName("필터 파라미터(status, channel, q) 전달 → service 호출 시 그대로 위임")
        void listWithFilters() throws Exception {
            given(campaignService.getCampaignList(any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            mockMvc.perform(get(BASE_URL)
                            .param("status", "ACTIVE")
                            .param("channel", "PUSH")
                            .param("q", "검색어"))
                    .andExpect(status().isOk());

            verify(campaignService).getCampaignList(
                    eq(PROJECT_ID),
                    eq(CampaignStatus.ACTIVE),
                    eq(Channel.PUSH),
                    eq("검색어"),
                    any()
            );
        }
    }
}
