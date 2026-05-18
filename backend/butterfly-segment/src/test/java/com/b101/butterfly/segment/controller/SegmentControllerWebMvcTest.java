package com.b101.butterfly.segment.controller;

import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.config.SegmentJacksonConfig;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import com.b101.butterfly.segment.domain.condition.validation.DepthGuard;
import com.b101.butterfly.segment.domain.condition.validation.OperatorCompatibilityValidator;
import com.b101.butterfly.segment.domain.condition.validation.PeriodValidator;
import com.b101.butterfly.segment.domain.condition.validation.StructureValidator;
import com.b101.butterfly.segment.domain.condition.validation.TypeFieldValidator;
import com.b101.butterfly.segment.dto.request.CreateSegmentRequest;
import com.b101.butterfly.segment.dto.response.CreateSegmentResponse;
import com.b101.butterfly.segment.dto.response.FavoritesResponse;
import com.b101.butterfly.segment.dto.response.LinkedCampaignSummary;
import com.b101.butterfly.segment.dto.response.LinkedCampaignsResponse;
import com.b101.butterfly.segment.dto.response.RebuildResponse;
import com.b101.butterfly.segment.dto.response.SegmentConditionMetadataResponse;
import com.b101.butterfly.segment.dto.response.SegmentDetailResponse;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.exception.SegmentValidationExceptionHandler;
import com.b101.butterfly.segment.dto.response.SegmentUserResponse;
import com.b101.butterfly.segment.service.SegmentConditionMetadataService;
import com.b101.butterfly.segment.service.SegmentFavoriteService;
import com.b101.butterfly.segment.service.SegmentRebuildService;
import com.b101.butterfly.segment.service.SegmentService;
import com.b101.butterfly.segment.service.SegmentUsersService;
import com.b101.butterfly.segment.service.preview.PreviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SegmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("api")
@Import({
    SegmentValidationExceptionHandler.class,
    SegmentJacksonConfig.class,
    ConditionTreeValidator.class,
    StructureValidator.class,
    TypeFieldValidator.class,
    OperatorCompatibilityValidator.class,
    PeriodValidator.class,
    DepthGuard.class
})
class SegmentControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private SegmentService segmentService;
    @MockitoBean private SegmentConditionMetadataService segmentConditionMetadataService;
    @MockitoBean private SegmentFavoriteService segmentFavoriteService;
    @MockitoBean private PreviewService previewService;
    @MockitoBean private SegmentRebuildService segmentRebuildService;
    @MockitoBean private SegmentUsersService segmentUsersService;

    /**
     * 슬라이스 테스트는 {@code LocalAuthFilter} 가 없으므로 SecurityContext 가 비어있다.
     * 컨트롤러의 {@code currentMemberId()} 가 {@code Authentication.getName()} 을 파싱하므로
     * 테스트마다 memberId="17" 인 Authentication 을 주입한다.
     */
    @BeforeEach
    void setSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("17", "", List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /segments — 정상 요청은 201 + ApiResponse 래핑")
    void createReturnsCreated() throws Exception {
        CreateSegmentRequest body = new CreateSegmentRequest("VIP", "설명", validTree());
        CreateSegmentResponse response =
                new CreateSegmentResponse(
                        100L, "VIP", "설명", BuildStatus.PENDING, null, null, null, 3, 17L,
                        LocalDateTime.now());
        when(segmentService.create(eq(1L), eq(17L), any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/projects/1/segments")
                                .header("X-Member-Id", 17L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.segmentId").value(100))
                .andExpect(jsonPath("$.data.buildStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST /segments — 빈 conditions는 SEGMENT_400_INVALID_CONDITION_TREE 반환")
    void createWithEmptyConditionsReturns400() throws Exception {
        GroupNode emptyTree = new GroupNode(LogicOp.AND, List.of());
        CreateSegmentRequest body = new CreateSegmentRequest("VIP", "설명", emptyTree);

        mockMvc.perform(
                        post("/api/v1/projects/1/segments")
                                .header("X-Member-Id", 17L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEGMENT_400_INVALID_CONDITION_TREE"));
    }

    @Test
    @DisplayName("POST /segments — name 길이 위반은 INVALID_REQUEST 반환")
    void createWithNameTooLongReturnsInvalidRequest() throws Exception {
        String longName = "a".repeat(31);
        CreateSegmentRequest body = new CreateSegmentRequest(longName, "설명", validTree());

        mockMvc.perform(
                        post("/api/v1/projects/1/segments")
                                .header("X-Member-Id", 17L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("GET /segments — 페이지 응답")
    void listReturnsPage() throws Exception {
        when(segmentService.list(eq(1L), eq(17L), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));

        mockMvc.perform(
                        get("/api/v1/projects/1/segments?page=0&size=20").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /segments/condition-metadata — 사용자 속성 메타데이터 응답")
    void conditionMetadataReturnsResponse() throws Exception {
        when(segmentConditionMetadataService.getMetadata())
                .thenReturn(
                        new SegmentConditionMetadataResponse(
                                List.of(
                                        new SegmentConditionMetadataResponse.UserAttributeResponse(
                                                "signedUpAt",
                                                "가입일",
                                                "date",
                                                List.of("eq", "neq")))));

        mockMvc.perform(
                        get("/api/v1/projects/1/segments/condition-metadata")
                                .header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userAttributes[0].key").value("signedUpAt"))
                .andExpect(jsonPath("$.data.userAttributes[0].label").value("가입일"))
                .andExpect(jsonPath("$.data.userAttributes[0].valueType").value("date"));
    }

    @Test
    @DisplayName("GET /segments/{id} — 상세 응답")
    void detailReturnsResponse() throws Exception {
        when(segmentService.detail(eq(1L), eq(42L), eq(17L))).thenReturn(stubDetail(42L, "stub"));

        mockMvc.perform(get("/api/v1/projects/1/segments/42").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segmentId").value(42));
    }

    @Test
    @DisplayName("PUT /segments/{id} — 정상 요청은 200")
    void updateReturnsOk() throws Exception {
        CreateSegmentRequest body = new CreateSegmentRequest("renamed", "desc", validTree());
        when(segmentService.update(eq(1L), eq(42L), eq(17L), any()))
                .thenReturn(stubDetail(42L, "renamed"));

        mockMvc.perform(
                        put("/api/v1/projects/1/segments/42")
                                .header("X-Member-Id", 17L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("renamed"));
    }

    @Test
    @DisplayName("DELETE /segments/{id} — 200 + ApiResponse.ok()")
    void deleteReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/1/segments/42").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /segments/preview — PreviewService 응답 반환")
    void previewReturnsResponse() throws Exception {
        when(previewService.preview(eq(1L), any()))
                .thenReturn(
                        new com.b101.butterfly.segment.dto.response.PreviewResponse(
                                5120, "HLL", LocalDateTime.now()));

        String body =
                objectMapper.writeValueAsString(
                        new java.util.HashMap<String, Object>() {
                            {
                                put("conditionTree", validTree());
                            }
                        });

        mockMvc.perform(
                        post("/api/v1/projects/1/segments/preview")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("HLL"))
                .andExpect(jsonPath("$.data.estimatedCount").value(5120));
    }

    @Test
    @DisplayName("POST /segments/{id}/rebuild — service 호출 + 202 Accepted")
    void rebuildReturnsAccepted() throws Exception {
        when(segmentRebuildService.requestRebuild(eq(1L), eq(42L), eq(17L)))
                .thenReturn(new RebuildResponse(42L, BuildStatus.PENDING, LocalDateTime.now()));

        mockMvc.perform(
                        post("/api/v1/projects/1/segments/42/rebuild").header("X-Member-Id", 17L))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.segmentId").value(42L));
    }

    @Test
    @DisplayName("GET /segments/{id}/campaigns — service 의 status 별 그룹화 응답을 그대로 반환")
    void linkedCampaignsReturnsGroupedResponse() throws Exception {
        LinkedCampaignSummary active1 = new LinkedCampaignSummary(101L, "스프링 캠페인", "ACTIVE");
        LinkedCampaignSummary paused1 = new LinkedCampaignSummary(102L, "여름 캠페인", "PAUSED");
        LinkedCampaignSummary draft1 = new LinkedCampaignSummary(103L, "초안 캠페인", "DRAFT");
        LinkedCampaignsResponse response = new LinkedCampaignsResponse(
                List.of(active1),
                List.of(paused1),
                List.of(draft1),
                List.of(),
                3);
        when(segmentService.listLinkedCampaigns(eq(1L), eq(42L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/1/segments/42/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.active[0].campaignId").value(101))
                .andExpect(jsonPath("$.data.active[0].campaignStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.paused[0].campaignId").value(102))
                .andExpect(jsonPath("$.data.draft[0].campaignId").value(103))
                .andExpect(jsonPath("$.data.ended").isEmpty());
    }

    @Test
    @DisplayName("GET /segments/{id}/campaigns — campaign 모듈 미합류 시 빈 그룹")
    void linkedCampaignsReturnsEmptyGroupsWhenStubbed() throws Exception {
        when(segmentService.listLinkedCampaigns(eq(1L), eq(42L)))
                .thenReturn(new LinkedCampaignsResponse(
                        List.of(), List.of(), List.of(), List.of(), 0));

        mockMvc.perform(get("/api/v1/projects/1/segments/42/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    @DisplayName("GET /segments/{id}/users — service 반환을 그대로 페이지로 응답")
    void usersReturnsServicePage() throws Exception {
        SegmentUserResponse u1 =
                new SegmentUserResponse(100L, "ext-100", "a@x.com", null, LocalDateTime.now());
        SegmentUserResponse u2 =
                new SegmentUserResponse(200L, "ext-200", "b@x.com", null, LocalDateTime.now());
        when(segmentUsersService.listUsers(eq(1L), eq(42L), any()))
                .thenReturn(PageResponse.of(List.of(u1, u2), 0, 20, 2));

        mockMvc.perform(get("/api/v1/projects/1/segments/42/users?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].userId").value(100))
                .andExpect(jsonPath("$.data.content[0].externalId").value("ext-100"))
                .andExpect(jsonPath("$.data.content[1].userId").value(200));
    }

    @Test
    @DisplayName("GET /segments/favorites — service 반환을 그대로 응답")
    void favoritesReturnsServiceResult() throws Exception {
        when(segmentFavoriteService.list(eq(1L), eq(17L)))
                .thenReturn(
                        new FavoritesResponse(
                                List.of(
                                        new FavoritesResponse.FavoriteItem(
                                                1024L,
                                                "검정 구두 260 구매 유저",
                                                1820,
                                                LocalDateTime.of(2026, 4, 26, 9, 0))),
                                List.of()));

        mockMvc.perform(get("/api/v1/projects/1/segments/favorites").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorites[0].segmentId").value(1024L))
                .andExpect(jsonPath("$.data.favorites[0].name").value("검정 구두 260 구매 유저"))
                .andExpect(jsonPath("$.data.favorites[0].estimatedCount").value(1820))
                .andExpect(jsonPath("$.data.recentlyUsed").isArray())
                .andExpect(jsonPath("$.data.recentlyUsed").isEmpty());
    }

    @Test
    @DisplayName("POST /segments/{id}/favorite — 200 OK + service 호출")
    void addFavoriteReturnsOk() throws Exception {
        mockMvc.perform(
                        post("/api/v1/projects/1/segments/42/favorite").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /segments/{id}/favorite — 200 OK + service 호출")
    void removeFavoriteReturnsOk() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/projects/1/segments/42/favorite").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private static GroupNode validTree() {
        return new GroupNode(
                LogicOp.AND,
                List.of(
                        new TagNode(
                                "region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"))));
    }

    private static SegmentDetailResponse stubDetail(long id, String name) {
        return new SegmentDetailResponse(
                id,
                name,
                "desc",
                null,
                3,
                null,
                null,
                null,
                BuildStatus.PENDING,
                0L,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(),
                false);
    }
}
