package com.example.demo.common.web;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 애플리케이션 전역 예외 처리.
 *
 * <p>개별 도메인 예외(TodoNotFoundException 등)를 알지 못한다.
 * 공통 타입 {@link BusinessException} 하나만 처리하므로 도메인이 늘어도 이 파일은 그대로다.
 * HTTP로의 번역은 여기서만 일어나고, 도메인/유스케이스 계층은 HTTP를 모른다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 모든 업무 예외의 단일 진입점. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = toHttpStatus(errorCode);
        if (status.is5xxServerError()) {
            log.error("업무 예외(5xx): {}", e.getMessage(), e);
        } else {
            log.debug("업무 예외: {}", e.getMessage());
        }
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), errorCode.getTitle(), e.getMessage()));
    }

    /** @Valid 검증 실패. 어느 필드가 왜 틀렸는지 함께 돌려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, ErrorCode.INVALID_INPUT.getTitle(),
                        "요청 값이 올바르지 않습니다.", fieldErrors));
    }

    /** 본문 JSON이 깨졌거나 타입이 맞지 않는 경우. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("요청 본문 파싱 실패: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, ErrorCode.INVALID_INPUT.getTitle(),
                        "요청 본문을 읽을 수 없습니다."));
    }

    /** 경로 변수/쿼리 파라미터 타입 불일치(예: /api/todos/abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, ErrorCode.INVALID_INPUT.getTitle(),
                        "'" + e.getName() + "' 값이 올바르지 않습니다."));
    }

    /** 업무 예외로 분류되지 않은 잘못된 인자. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("처리되지 않은 IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, ErrorCode.INVALID_INPUT.getTitle(), e.getMessage()));
    }

    /** 마지막 안전망. 내부 메시지를 그대로 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, ErrorCode.INTERNAL_ERROR.getTitle(),
                        "서버 내부 오류가 발생했습니다."));
    }

    /** 에러 분류 → HTTP 상태 코드 번역. 애플리케이션에서 이 매핑이 존재하는 유일한 지점이다. */
    private HttpStatus toHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
