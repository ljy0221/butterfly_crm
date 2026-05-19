package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.response.DeleteCategoryResponse;
import com.b101.butterfly.event.dto.response.EventCategoryDetailResponse;
import com.b101.butterfly.event.dto.response.EventCategorySummaryResponse;
import com.b101.butterfly.event.dto.response.PropertyResponse;
import com.b101.butterfly.event.dto.response.RecentSampleResponse;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.exception.EventValidationExceptionHandler;
import com.b101.butterfly.event.service.EventCategoryService;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("api")
@Import({GlobalExceptionHandler.class, EventValidationExceptionHandler.class})
class EventCategoryControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private EventCategoryService eventCategoryService;

    private static final Long PROJECT_ID = 1L;
    private static final String CATEGORY_NAME = "purchase";
    private static final String BASE_URL = "/api/v1/projects/" + PROJECT_ID + "/events/categories";

    @Test
    @DisplayName("GET /events/categories — 카테고리 리스트 반환")
    void list_returnsOk() throws Exception {
        EventCategorySummaryResponse summary = new EventCategorySummaryResponse(
                10L, "purchase", "구매 완료", "구매 완료",
                "CUSTOM", 3, 0L, 100.0, null);
        when(eventCategoryService.list(eq(PROJECT_ID))).thenReturn(List.of(summary));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryId").value(10))
                .andExpect(jsonPath("$.data[0].categoryName").value("purchase"))
                .andExpect(jsonPath("$.data[0].displayName").value("구매 완료"))
                .andExpect(jsonPath("$.data[0].kind").value("CUSTOM"));
    }

    @Test
    @DisplayName("GET /events/categories/{name} — 단건 상세 반환")
    void get_returnsOk() throws Exception {
        EventCategoryDetailResponse detail = new EventCategoryDetailResponse(
                10L, PROJECT_ID, "purchase", "구매 완료", "구매 완료",
                "CUSTOM", 0L, 100.0, null,
                "2026-05-11T10:00", "2026-05-11T10:00");
        when(eventCategoryService.get(PROJECT_ID, CATEGORY_NAME)).thenReturn(detail);

        mockMvc.perform(get(BASE_URL + "/" + CATEGORY_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("purchase"));
    }

    @Test
    @DisplayName("GET /events/categories/{name} — 미등록 카테고리는 404")
    void get_notFound() throws Exception {
        when(eventCategoryService.get(PROJECT_ID, "unknown"))
                .thenThrow(new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /events/categories/{name}/schema — 필드 리스트 반환")
    void getSchema_returnsOk() throws Exception {
        when(eventCategoryService.getSchema(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(List.of(new PropertyResponse("amount", "number", true, null)));

        mockMvc.perform(get(BASE_URL + "/" + CATEGORY_NAME + "/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("amount"))
                .andExpect(jsonPath("$.data[0].type").value("number"));
    }

    @Test
    @DisplayName("POST /events/categories — 생성 시 201")
    void create_returnsCreated() throws Exception {
        EventCategoryDetailResponse detail = new EventCategoryDetailResponse(
                11L, PROJECT_ID, "wishlist_add", "관심상품 추가", "관심상품 추가",
                "CUSTOM", 0L, 100.0, null,
                "2026-05-11T10:00", "2026-05-11T10:00");
        when(eventCategoryService.create(eq(PROJECT_ID), any(), any())).thenReturn(detail);

        String body = """
                {
                  "categoryName": "wishlist_add",
                  "displayName": "관심상품 추가",
                  "properties": [
                    { "key": "product_id", "type": "string", "required": true }
                  ]
                }
                """;
        mockMvc.perform(post(BASE_URL)
                        .param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categoryName").value("wishlist_add"));
    }

    @Test
    @DisplayName("POST /events/categories — categoryName 정규식 위반은 400")
    void create_invalidNameRegex() throws Exception {
        String body = """
                {
                  "categoryName": "X-bad-name",
                  "displayName": "잘못된 이름"
                }
                """;
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /events/categories — 중복 이름은 409")
    void create_duplicateName() throws Exception {
        when(eventCategoryService.create(eq(PROJECT_ID), any(), any()))
                .thenThrow(new BusinessException(EventErrorCode.CATEGORY_ALREADY_EXISTS));

        String body = """
                {
                  "categoryName": "purchase",
                  "displayName": "중복 시도"
                }
                """;
        mockMvc.perform(post(BASE_URL)
                        .param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("PUT /events/categories/{name}/schema — 새 버전 적용")
    void updateSchema_returnsOk() throws Exception {
        when(eventCategoryService.updateSchema(eq(PROJECT_ID), eq(CATEGORY_NAME), any(), any()))
                .thenReturn(List.of(new PropertyResponse("amount", "number", true, null)));

        String body = """
                {
                  "properties": [
                    { "key": "amount", "type": "number", "required": true }
                  ]
                }
                """;
        mockMvc.perform(put(BASE_URL + "/" + CATEGORY_NAME + "/schema")
                        .param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("amount"));
    }

    @Test
    @DisplayName("DELETE /events/categories/{id} — 삭제 시 200 + categoryId")
    void delete_returnsOk() throws Exception {
        when(eventCategoryService.delete(PROJECT_ID, 99L))
                .thenReturn(new DeleteCategoryResponse(99L));

        mockMvc.perform(delete(BASE_URL + "/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(99));
    }

    @Test
    @DisplayName("DELETE /events/categories/{id} — BUILTIN 카테고리는 403")
    void delete_builtinProtected() throws Exception {
        when(eventCategoryService.delete(PROJECT_ID, 1L))
                .thenThrow(new BusinessException(EventErrorCode.CATEGORY_BUILTIN_PROTECTED));

        mockMvc.perform(delete(BASE_URL + "/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_BUILTIN_PROTECTED"));
    }

    @Test
    @DisplayName("GET /events/categories/{name}/sample — 최근 발생 1건 반환")
    void getRecentSample_returnsOk() throws Exception {
        when(eventCategoryService.getRecentSample(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(new RecentSampleResponse("2026-05-11T10:00",
                        Map.of("amount", 12345, "currency", "KRW")));

        mockMvc.perform(get(BASE_URL + "/" + CATEGORY_NAME + "/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occurredAt").value("2026-05-11T10:00"))
                .andExpect(jsonPath("$.data.eventData.amount").value(12345));
    }

    @Test
    @DisplayName("GET /events/categories/{name}/sample — 발생 이력 없으면 null data")
    void getRecentSample_nullable() throws Exception {
        when(eventCategoryService.getRecentSample(PROJECT_ID, CATEGORY_NAME)).thenReturn(null);

        mockMvc.perform(get(BASE_URL + "/" + CATEGORY_NAME + "/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
