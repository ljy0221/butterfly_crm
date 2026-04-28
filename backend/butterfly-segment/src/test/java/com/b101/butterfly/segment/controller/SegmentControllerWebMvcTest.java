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
import com.b101.butterfly.segment.dto.response.SegmentDetailResponse;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.exception.SegmentValidationExceptionHandler;
import com.b101.butterfly.segment.service.SegmentFavoriteService;
import com.b101.butterfly.segment.service.SegmentService;
import com.b101.butterfly.segment.service.preview.PreviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    @MockitoBean private SegmentFavoriteService segmentFavoriteService;
    @MockitoBean private PreviewService previewService;

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
                        post("/api/projects/1/segments")
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
                        post("/api/projects/1/segments")
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
                        post("/api/projects/1/segments")
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
                        get("/api/projects/1/segments?page=0&size=20").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /segments/{id} — 상세 응답")
    void detailReturnsResponse() throws Exception {
        when(segmentService.detail(eq(1L), eq(42L), eq(17L))).thenReturn(stubDetail(42L, "stub"));

        mockMvc.perform(get("/api/projects/1/segments/42").header("X-Member-Id", 17L))
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
                        put("/api/projects/1/segments/42")
                                .header("X-Member-Id", 17L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("renamed"));
    }

    @Test
    @DisplayName("DELETE /segments/{id} — 200 + ApiResponse.ok()")
    void deleteReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/projects/1/segments/42"))
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
                        post("/api/projects/1/segments/preview")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("HLL"))
                .andExpect(jsonPath("$.data.estimatedCount").value(5120));
    }

    @Test
    @DisplayName("POST /segments/{id}/rebuild — 202 Accepted")
    void rebuildReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/projects/1/segments/42/rebuild"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.buildStatus").value("BUILDING"));
    }

    @Test
    @DisplayName("GET /segments/{id}/campaigns — 더미 빈 그룹")
    void linkedCampaignsReturnsEmptyGroups() throws Exception {
        mockMvc.perform(get("/api/projects/1/segments/42/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    @DisplayName("GET /segments/{id}/users — 더미 빈 페이지")
    void usersReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/projects/1/segments/42/users?page=0&size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /segments/favorites — 더미 빈 분리 응답")
    void favoritesReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/projects/1/segments/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorites").isArray())
                .andExpect(jsonPath("$.data.recentlyUsed").isArray());
    }

    @Test
    @DisplayName("POST /segments/{id}/favorite — 200 OK + service 호출")
    void addFavoriteReturnsOk() throws Exception {
        mockMvc.perform(
                        post("/api/projects/1/segments/42/favorite").header("X-Member-Id", 17L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /segments/{id}/favorite — 200 OK + service 호출")
    void removeFavoriteReturnsOk() throws Exception {
        mockMvc.perform(
                        delete("/api/projects/1/segments/42/favorite").header("X-Member-Id", 17L))
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
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(),
                false);
    }
}
