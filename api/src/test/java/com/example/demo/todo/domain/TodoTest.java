package com.example.demo.todo.domain;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 도메인 모델 단위 테스트. 스프링 컨텍스트도, DB도 띄우지 않는다.
 */
class TodoTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("새로 만든 Todo는 식별자가 없고 생성/수정 시각이 같다")
    void create() {
        Todo todo = Todo.create("할 일", "설명", false, NOW);

        assertThat(todo.isNew()).isTrue();
        assertThat(todo.getId()).isNull();
        assertThat(todo.getCreatedAt()).isEqualTo(NOW);
        assertThat(todo.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("수정하면 updatedAt만 바뀌고 createdAt은 보존된다")
    void update() {
        Todo todo = Todo.reconstitute(1L, "원래", null, false, NOW, NOW);
        Instant later = NOW.plusSeconds(60);

        todo.update("바뀜", "새 설명", true, later);

        assertThat(todo.getTitle()).isEqualTo("바뀜");
        assertThat(todo.isCompleted()).isTrue();
        assertThat(todo.getCreatedAt()).isEqualTo(NOW);
        assertThat(todo.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("title이 비면 도메인이 공통 예외로 거부한다")
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> Todo.create("   ", null, false, NOW))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("title");
    }

    @Test
    @DisplayName("title 길이 제한을 넘기면 거부하고 에러 분류는 INVALID_INPUT이다")
    void rejectsTooLongTitle() {
        String tooLong = "가".repeat(Todo.MAX_TITLE_LENGTH + 1);

        assertThatThrownBy(() -> Todo.create(tooLong, null, false, NOW))
                .isInstanceOf(InvalidInputException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(InvalidInputException.class))
                .extracting(InvalidInputException::getErrorCode)
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
