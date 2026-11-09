package com.example.demo.common.exception;

/**
 * 입력 값이 도메인 규칙을 위반했을 때의 공통 예외.
 *
 * <p>어댑터의 Bean Validation을 통과했더라도 도메인 불변식에 어긋나면 이 예외가 난다.
 */
public class InvalidInputException extends BusinessException {

    public InvalidInputException(String message) {
        super(ErrorCode.INVALID_INPUT, message);
    }
}
