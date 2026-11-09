package com.example.demo.usecase.dto;

/**
 * 등록 유스케이스 입력.
 *
 * <p>유스케이스 경계로 들어오는 타입은 항상 {@code ~AppRequest} 로 끝난다.
 * HTTP/JSON에도, 영속성에도 의존하지 않으므로 웹이 아닌 호출자
 * (배치, 메시지 컨슈머 등)도 같은 타입으로 유스케이스를 부를 수 있다.
 */
public record AddTodoAppRequest(
        String title,
        String description,
        boolean completed
) {
}
