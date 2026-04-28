package com.b101.butterfly.segment.controller;

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
import com.b101.butterfly.segment.config.SegmentJacksonConfig;
import com.b101.butterfly.segment.dto.request.CreateSegmentRequest;
import com.b101.butterfly.segment.exception.SegmentValidationExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    @Test
    @DisplayName("POST /segments — 정상 요청은 201 + ApiResponse 래핑")
    void createReturnsCreated() throws Exception {
        CreateSegmentRequest body = new CreateSegmentRequest("VIP", "설명", validTree());

        mockMvc.perform(
                        post("/api/projects/1/segments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("VIP"))
                .andExpect(jsonPath("$.data.buildStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST /segments — 빈 conditions는 SEGMENT_400_INVALID_CONDITION_TREE 반환")
    void createWithEmptyConditionsReturns400() throws Exception {
        GroupNode emptyTree = new GroupNode(LogicOp.AND, List.of());
        CreateSegmentRequest body = new CreateSegmentRequest("VIP", "설명", emptyTree);

        mockMvc.perform(
                        post("/api/projects/1/segments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SEGMENT_400_INVALID_CONDITION_TREE"));
    }

    @Test
    @DisplayName("POST /segments — name 길이 위반은 INVALID_REQUEST 반환")
    void createWithNameTooLongReturnsInvalidRequest() throws Exception {
        String longName = "a".repeat(31);
        CreateSegmentRequest body = new CreateSegmentRequest(longName, "설명", validTree());

        mockMvc.perform(
                        post("/api/projects/1/segments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("GET /segments — 더미 빈 페이지 응답")
    void listReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/projects/1/segments?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /segments/{id} — 더미 상세 응답")
    void detailReturnsStub() throws Exception {
        mockMvc.perform(get("/api/projects/1/segments/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segmentId").value(42));
    }

    @Test
    @DisplayName("PUT /segments/{id} — 정상 요청은 200")
    void updateReturnsOk() throws Exception {
        CreateSegmentRequest body = new CreateSegmentRequest("renamed", "desc", validTree());

        mockMvc.perform(
                        put("/api/projects/1/segments/42")
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
    @DisplayName("POST /segments/preview — 더미 PreviewResponse")
    void previewReturnsStub() throws Exception {
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
                .andExpect(jsonPath("$.data.method").value("HLL"));
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
    @DisplayName("POST /segments/{id}/favorite — 200 OK")
    void addFavoriteReturnsOk() throws Exception {
        mockMvc.perform(post("/api/projects/1/segments/42/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /segments/{id}/favorite — 200 OK")
    void removeFavoriteReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/projects/1/segments/42/favorite"))
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
}
