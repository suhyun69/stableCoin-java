package com.example.demo.todo.domain;

import com.example.demo.common.exception.InvalidInputException;

import java.time.Instant;
import java.util.Objects;

/**
 * Todo 도메인 모델.
 *
 * <p>프레임워크/영속성 기술에 대한 의존이 전혀 없다. JPA 애너테이션도, 스프링 애너테이션도 없다.
 * 영속성 매핑은 어댑터 쪽의 {@code TodoJpaEntity}가 담당한다.
 */
public class Todo {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final Long id;
    private String title;
    private String description;
    private boolean completed;
    private final Instant createdAt;
    private Instant updatedAt;

    private Todo(Long id, String title, String description, boolean completed,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = requireValidTitle(title);
        this.description = requireValidDescription(description);
        this.completed = completed;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수입니다.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt은 필수입니다.");
    }

    /** 아직 저장되지 않은 새 Todo. id는 영속성 어댑터가 채운다. */
    public static Todo create(String title, String description, boolean completed, Instant now) {
        return new Todo(null, title, description, completed, now, now);
    }

    /** 저장소에서 읽어온 상태를 도메인 모델로 되살린다. 어댑터에서만 사용한다. */
    public static Todo reconstitute(Long id, String title, String description, boolean completed,
                                    Instant createdAt, Instant updatedAt) {
        Objects.requireNonNull(id, "id는 필수입니다.");
        return new Todo(id, title, description, completed, createdAt, updatedAt);
    }

    public void update(String title, String description, boolean completed, Instant now) {
        this.title = requireValidTitle(title);
        this.description = requireValidDescription(description);
        this.completed = completed;
        this.updatedAt = Objects.requireNonNull(now, "updatedAt은 필수입니다.");
    }

    /** 아직 식별자가 없는, 저장 전 상태인지 여부. */
    public boolean isNew() {
        return id == null;
    }

    private static String requireValidTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidInputException("title은 필수입니다.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new InvalidInputException("title은 " + MAX_TITLE_LENGTH + "자를 넘을 수 없습니다.");
        }
        return title;
    }

    private static String requireValidDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidInputException(
                    "description은 " + MAX_DESCRIPTION_LENGTH + "자를 넘을 수 없습니다.");
        }
        return description;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
