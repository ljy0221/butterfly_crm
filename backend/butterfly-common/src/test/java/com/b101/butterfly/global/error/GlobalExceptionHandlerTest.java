package com.b101.butterfly.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.b101.butterfly.global.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BusinessException은 ErrorCode의 status + ApiResponse.fail로 매핑된다")
    void businessException() {
        BusinessException ex = new BusinessException(CommonErrorCode.NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> resp = handler.handleBusiness(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiResponse<Void> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.error().code()).isEqualTo("COMMON_004");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException은 첫 fieldError를 detail로 400 응답")
    void validation() throws Exception {
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy"), -1);
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(java.util.List.of(
                new FieldError("obj", "name", "must not be blank")));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, br);

        ResponseEntity<ApiResponse<Void>> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().code()).isEqualTo("COMMON_001");
        assertThat(resp.getBody().error().message()).contains("name").contains("must not be blank");
    }

    @Test
    @DisplayName("ConstraintViolationException은 첫 violation을 detail로 400 응답")
    void constraintViolation() {
        ConstraintViolation<?> v = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("arg.field");
        when(v.getPropertyPath()).thenReturn(path);
        when(v.getMessage()).thenReturn("must be positive");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(v));

        ResponseEntity<ApiResponse<Void>> resp = handler.handleConstraintViolation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().message()).contains("arg.field").contains("must be positive");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException은 400 + 친화 메시지 반환")
    void notReadable() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "bad json",
                new RuntimeException("Unexpected character"),
                new ServletServerHttpRequest(new MockHttpServletRequest()));

        ResponseEntity<ApiResponse<Void>> resp = handler.handleNotReadable(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().code()).isEqualTo("COMMON_001");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException은 파라미터명 + 기대 타입을 메시지에 담아 400 반환")
    void typeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "templateId", null, new IllegalArgumentException());

        ResponseEntity<ApiResponse<Void>> resp = handler.handleTypeMismatch(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().message()).contains("templateId").contains("Long");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException은 400 + 누락 파라미터명 반환")
    void missingParam() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("projectId", "Long");

        ResponseEntity<ApiResponse<Void>> resp = handler.handleMissingParam(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().message()).contains("projectId");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException은 405 METHOD_NOT_ALLOWED 반환")
    void methodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ApiResponse<Void>> resp = handler.handleMethodNotSupported(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody().error().code()).isEqualTo("COMMON_006");
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException은 415 UNSUPPORTED_MEDIA_TYPE 반환")
    void mediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN,
                        java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiResponse<Void>> resp = handler.handleMediaTypeNotSupported(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resp.getBody().error().code()).isEqualTo("COMMON_007");
    }

    @Test
    @DisplayName("그 외 Exception은 500 INTERNAL_ERROR로 매핑")
    void unknown() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleUnknown(new RuntimeException("boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().error().code()).isEqualTo("COMMON_999");
    }

    // dummy method for MethodParameter construction
    @SuppressWarnings("unused")
    private void dummy() {
    }
}
