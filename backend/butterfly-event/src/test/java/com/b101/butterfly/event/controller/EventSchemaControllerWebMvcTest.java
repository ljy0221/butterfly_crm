package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.response.EventSchemaResponse;
import com.b101.butterfly.event.entity.FieldType;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.exception.EventValidationExceptionHandler;
import com.b101.butterfly.event.service.EventSchemaService;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.error.GlobalExceptionHandler;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventSchemaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, EventValidationExceptionHandler.class})
class EventSchemaControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private EventSchemaService eventSchemaService;

    private static final Long PROJECT_ID = 1L;
    private static final Long CATEGORY_ID = 10L;
    private static final String BASE_URL =
            "/api/v1/projects/" + PROJECT_ID + "/event-categories/" + CATEGORY_ID + "/schemas";

    private EventSchemaResponse sampleResponse(int version, boolean isCurrent) {
        return new EventSchemaResponse(
                (long) version * 100,
                CATEGORY_ID,
                version,
                isCurrent,
                LocalDateTime.of(2026, 5, 6, 10, 0),
                List.of(new EventSchemaResponse.SchemaFieldResponse(1L, "amount", FieldType.NUMBER, true, false)),
                LocalDateTime.of(2026, 5, 6, 10, 0)
        );
    }

    private String validBody() {
        return """
                {
                  "fields": [
                    { "fieldName": "amount", "fieldType": "NUMBER", "isRequired": true, "isPromoted": false }
                  ]
                }
                """;
    }

    // ─── POST ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /schemas — 정상 요청: 201 Created, version=1")
    void createSchema_validRequest_returns201() throws Exception {
        when(eventSchemaService.createSchema(eq(PROJECT_ID), eq(CATEGORY_ID), any(), any()))
                .thenReturn(sampleResponse(1, true));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andExpect(jsonPath("$.data.fields[0].fieldName").value("amount"));
    }

    @Test
    @DisplayName("POST /schemas — fields 빈 배열 → 400 validation 실패")
    void createSchema_emptyFields_returns400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /schemas — fields 누락 → 400 validation 실패")
    void createSchema_missingFields_returns400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /schemas — fieldName 빈 문자열 → 400 validation 실패")
    void createSchema_blankFieldName_returns400() throws Exception {
        String body = """
                {"fields": [{"fieldName": "", "fieldType": "STRING", "isRequired": false, "isPromoted": false}]}
                """;
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /schemas — SCHEMA_ALREADY_EXISTS → 409 Conflict")
    void createSchema_alreadyExists_returns409() throws Exception {
        when(eventSchemaService.createSchema(any(), any(), any(), any()))
                .thenThrow(new BusinessException(EventErrorCode.SCHEMA_ALREADY_EXISTS));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SCHEMA_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /schemas — CATEGORY_NOT_FOUND → 404")
    void createSchema_categoryNotFound_returns404() throws Exception {
        when(eventSchemaService.createSchema(any(), any(), any(), any()))
                .thenThrow(new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    // ─── PUT ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /schemas — 정상 요청: 200 OK, version 증가")
    void updateSchema_validRequest_returns200() throws Exception {
        when(eventSchemaService.updateSchema(eq(PROJECT_ID), eq(CATEGORY_ID), any(), any()))
                .thenReturn(sampleResponse(2, true));

        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    @DisplayName("PUT /schemas — SCHEMA_NOT_FOUND → 404")
    void updateSchema_schemaNotFound_returns404() throws Exception {
        when(eventSchemaService.updateSchema(any(), any(), any(), any()))
                .thenThrow(new BusinessException(EventErrorCode.SCHEMA_NOT_FOUND));

        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SCHEMA_NOT_FOUND"));
    }

    // ─── GET /current ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /schemas/current — 200 OK, 현행 스키마 반환")
    void getCurrentSchema_returnsOk() throws Exception {
        when(eventSchemaService.getCurrentSchema(PROJECT_ID, CATEGORY_ID))
                .thenReturn(sampleResponse(3, true));

        mockMvc.perform(get(BASE_URL + "/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.isCurrent").value(true));
    }

    @Test
    @DisplayName("GET /schemas/current — SCHEMA_NOT_FOUND → 404")
    void getCurrentSchema_notFound_returns404() throws Exception {
        when(eventSchemaService.getCurrentSchema(any(), any()))
                .thenThrow(new BusinessException(EventErrorCode.SCHEMA_NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SCHEMA_NOT_FOUND"));
    }

    // ─── GET (list) ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /schemas — 200 OK, 버전 목록 배열 반환")
    void listSchemas_returnsOk() throws Exception {
        when(eventSchemaService.listSchemas(PROJECT_ID, CATEGORY_ID))
                .thenReturn(List.of(sampleResponse(2, true), sampleResponse(1, false)));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[1].version").value(1));
    }

    @Test
    @DisplayName("GET /schemas — 스키마 없음 → 200 OK, 빈 배열")
    void listSchemas_noSchemas_returnsEmptyArray() throws Exception {
        when(eventSchemaService.listSchemas(any(), any())).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
