package com.example.demo.usecase.impl;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.todo.application.TodoService;
import com.example.demo.usecase.ReadTodoUseCase;
import com.example.demo.usecase.dto.TodoAppResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 유스케이스 구현. 트랜잭션 경계가 여기에 있다(읽기 전용).
 */
@Service
@Transactional(readOnly = true)
public class ReadTodoUseCaseImpl implements ReadTodoUseCase {

    private final TodoService todoService;

    public ReadTodoUseCaseImpl(TodoService todoService) {
        this.todoService = todoService;
    }

    @Override
    public PageResult<TodoAppResponse> readAll(Boolean completed, PageQuery pageQuery) {
        return todoService.findAll(completed, pageQuery)
                .map(TodoAppResponse::from);
    }

    @Override
    public TodoAppResponse readById(Long id) {
        return TodoAppResponse.from(todoService.findById(id));
    }
}
