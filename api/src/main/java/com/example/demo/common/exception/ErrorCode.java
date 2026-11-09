package com.example.demo.common.exception;

/**
 * 애플리케이션 공통 에러 분류.
 *
 * <p>HTTP 상태 코드를 담지 않는다. HTTP로의 번역은 웹 계층
 * ({@code common.web.GlobalExceptionHandler})이 담당하므로,
 * 도메인은 이 enum에 의존해도 HTTP를 알게 되지 않는다.
 */
public enum ErrorCode {

    /** 요청한 리소스가 없음. */
    NOT_FOUND("Not Found"),

    /** 입력 값이 규칙에 맞지 않음. */
    INVALID_INPUT("Bad Request"),

    /** 현재 상태에서 수행할 수 없는 요청. */
    CONFLICT("Conflict"),

    /** 분류되지 않은 내부 오류. */
    INTERNAL_ERROR("Internal Server Error");

    private final String title;

    ErrorCode(String title) {
        this.title = title;
    }

    /** 응답 본문의 error 필드에 쓰이는 표기. */
    public String getTitle() {
        return title;
    }
}
