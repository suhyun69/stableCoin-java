package com.example.demo.todo.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 영속성 전용 모델. 도메인 모델과 분리돼 있어 테이블 구조가 바뀌어도
 * 도메인이 영향을 받지 않는다(매퍼만 수정하면 된다).
 */
@Entity
@Table(name = "todo")
public class TodoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TodoJpaEntity() {
    }

    TodoJpaEntity(Long id, String title, String description, boolean completed,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    void applyChanges(String title, String description, boolean completed, Instant updatedAt) {
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.updatedAt = updatedAt;
    }

    Long getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    boolean isCompleted() {
        return completed;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
