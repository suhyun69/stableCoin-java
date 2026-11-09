package com.example.demo.usecase.dto;

import com.example.demo.todo.domain.Todo;

import java.time.Instant;

/**
 * Todo 유스케이스 출력.
 *
 * <p>유스케이스 경계로 나가는 타입은 항상 {@code ~AppResponse} 로 끝난다.
 * 도메인 모델 {@link Todo} 는 이 타입에서 멈추고 웹 계층까지 넘어가지 않는다.
 * 변환은 유스케이스 계층의 책임이므로 변환 메서드를 여기에 둔다.
 */
public record TodoAppResponse(
        Long id,
        String title,
        String description,
        boolean completed,
        Instant createdAt,
        Instant updatedAt
) {
    public static TodoAppResponse from(Todo todo) {
        return new TodoAppResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.isCompleted(),
                todo.getCreatedAt(),
                todo.getUpdatedAt()
        );
    }
}
