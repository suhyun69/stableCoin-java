package com.example.demo.usecase.impl;

import com.example.demo.todo.application.TodoService;
import com.example.demo.todo.domain.Todo;
import com.example.demo.usecase.AddTodoUseCase;
import com.example.demo.usecase.dto.AddTodoAppRequest;
import com.example.demo.usecase.dto.TodoAppResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록 유스케이스 구현. 실제 동작은 Todo 도메인 서비스에 위임하고,
 * 돌아온 도메인 모델을 AppResponse로 변환해 경계 밖으로 내보낸다.
 *
 * <p>트랜잭션 경계는 이 계층에 있다. 여러 도메인 서비스를 호출하게 되더라도
 * 전부 한 트랜잭션으로 묶인다.
 */
@Service
public class AddTodoUseCaseImpl implements AddTodoUseCase {

    private final TodoService todoService;

    public AddTodoUseCaseImpl(TodoService todoService) {
        this.todoService = todoService;
    }

    @Override
    @Transactional
    public TodoAppResponse add(AddTodoAppRequest request) {
        Todo todo = todoService.create(
                request.title(),
                request.description(),
                request.completed()
        );
        return TodoAppResponse.from(todo);
    }
}
