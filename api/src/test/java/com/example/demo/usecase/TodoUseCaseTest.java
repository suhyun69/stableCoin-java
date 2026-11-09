package com.example.demo.usecase;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.todo.application.TodoService;
import com.example.demo.todo.domain.Todo;
import com.example.demo.usecase.dto.AddTodoAppRequest;
import com.example.demo.usecase.dto.TodoAppResponse;
import com.example.demo.usecase.dto.UpdateTodoAppRequest;
import com.example.demo.usecase.impl.AddTodoUseCaseImpl;
import com.example.demo.usecase.impl.DeleteTodoUseCaseImpl;
import com.example.demo.usecase.impl.ReadTodoUseCaseImpl;
import com.example.demo.usecase.impl.UpdateTodoUseCaseImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 유스케이스 구현체가 도메인 서비스로 위임하고, 도메인 모델을 AppResponse로
 * 변환해 내보내는지 확인한다. 스프링도 DB도 띄우지 않는다.
 */
class TodoUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private TodoService todoService;

    @BeforeEach
    void setUp() {
        todoService = mock(TodoService.class);
    }

    private static Todo sample(long id) {
        return Todo.reconstitute(id, "할 일", "설명", false, NOW, NOW);
    }

    @Test
    @DisplayName("AddTodoUseCase는 AppRequest를 풀어 create를 부르고 AppResponse를 돌려준다")
    void add() {
        when(todoService.create(anyString(), any(), anyBoolean())).thenReturn(sample(1L));
        AddTodoUseCase useCase = new AddTodoUseCaseImpl(todoService);

        TodoAppResponse result = useCase.add(new AddTodoAppRequest("할 일", "설명", true));

        verify(todoService).create("할 일", "설명", true);
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("할 일");
        assertThat(result.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("ReadTodoUseCase는 페이지 결과를 AppResponse로 변환하고 페이지 정보를 보존한다")
    void read() {
        PageQuery pageQuery = new PageQuery(0, 20);
        when(todoService.findAll(eq(true), eq(pageQuery)))
                .thenReturn(PageResult.of(List.of(sample(1L)), 0, 20, 1));
        when(todoService.findById(1L)).thenReturn(sample(1L));
        ReadTodoUseCase useCase = new ReadTodoUseCaseImpl(todoService);

        PageResult<TodoAppResponse> all = useCase.readAll(true, pageQuery);
        TodoAppResponse one = useCase.readById(1L);

        assertThat(all.content()).hasSize(1);
        assertThat(all.content().get(0)).isInstanceOf(TodoAppResponse.class);
        assertThat(all.totalElements()).isEqualTo(1);
        assertThat(all.totalPages()).isEqualTo(1);
        assertThat(one.id()).isEqualTo(1L);

        verify(todoService).findAll(true, pageQuery);
        verify(todoService).findById(1L);
    }

    @Test
    @DisplayName("UpdateTodoUseCase는 AppRequest의 id와 값을 그대로 전달한다")
    void update() {
        when(todoService.update(anyLong(), anyString(), any(), anyBoolean())).thenReturn(sample(7L));
        UpdateTodoUseCase useCase = new UpdateTodoUseCaseImpl(todoService);

        TodoAppResponse result = useCase.update(
                new UpdateTodoAppRequest(7L, "바뀜", "새 설명", true));

        verify(todoService).update(7L, "바뀜", "새 설명", true);
        assertThat(result.id()).isEqualTo(7L);
    }

    @Test
    @DisplayName("DeleteTodoUseCase는 TodoService.delete를 호출한다")
    void delete() {
        DeleteTodoUseCase useCase = new DeleteTodoUseCaseImpl(todoService);

        useCase.delete(3L);

        verify(todoService).delete(3L);
    }
}
