package com.example.demo.usecase.impl;

import com.example.demo.todo.application.TodoService;
import com.example.demo.todo.domain.Todo;
import com.example.demo.usecase.UpdateTodoUseCase;
import com.example.demo.usecase.dto.TodoAppResponse;
import com.example.demo.usecase.dto.UpdateTodoAppRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수정 유스케이스 구현. 트랜잭션 경계가 여기에 있다.
 */
@Service
public class UpdateTodoUseCaseImpl implements UpdateTodoUseCase {

    private final TodoService todoService;

    public UpdateTodoUseCaseImpl(TodoService todoService) {
        this.todoService = todoService;
    }

    @Override
    @Transactional
    public TodoAppResponse update(UpdateTodoAppRequest request) {
        Todo todo = todoService.update(
                request.id(),
                request.title(),
                request.description(),
                request.completed()
        );
        return TodoAppResponse.from(todo);
    }
}
