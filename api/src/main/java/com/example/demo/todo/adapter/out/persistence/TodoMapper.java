package com.example.demo.todo.adapter.out.persistence;

import com.example.demo.todo.domain.Todo;

/**
 * 도메인 모델 ↔ 영속성 엔티티 변환. 어댑터 내부 전용.
 */
final class TodoMapper {

    private TodoMapper() {
    }

    static Todo toDomain(TodoJpaEntity entity) {
        return Todo.reconstitute(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.isCompleted(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static TodoJpaEntity toNewEntity(Todo todo) {
        return new TodoJpaEntity(
                null,
                todo.getTitle(),
                todo.getDescription(),
                todo.isCompleted(),
                todo.getCreatedAt(),
                todo.getUpdatedAt()
        );
    }
}
