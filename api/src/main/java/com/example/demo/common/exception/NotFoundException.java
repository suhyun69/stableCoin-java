package com.example.demo.common.exception;

/**
 * 리소스를 찾지 못했을 때의 공통 예외. 각 도메인은 이 타입을 상속한다.
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    /** "Todo를 찾을 수 없습니다. id=3" 형태의 메시지를 만들어 준다. */
    public static NotFoundException of(String resourceName, Object id) {
        return new NotFoundException(resourceName + "를 찾을 수 없습니다. id=" + id);
    }
}
