package com.example.demo.common.exception;

/**
 * 모든 업무 예외의 최상위 타입.
 *
 * <p>각 도메인은 이 타입(또는 하위 타입)을 상속한 예외를 던지고,
 * 웹 계층은 개별 예외가 아니라 이 타입 하나만 알면 된다.
 * 도메인이 늘어도 예외 핸들러를 고칠 일이 없다.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
