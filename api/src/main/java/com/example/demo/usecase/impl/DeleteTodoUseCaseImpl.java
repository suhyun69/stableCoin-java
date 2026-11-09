package com.example.demo.usecase.impl;

import com.example.demo.todo.application.TodoService;
import com.example.demo.usecase.DeleteTodoUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삭제 유스케이스 구현. 트랜잭션 경계가 여기에 있다.
 */
@Service
public class DeleteTodoUseCaseImpl implements DeleteTodoUseCase {

    private final TodoService todoService;

    public DeleteTodoUseCaseImpl(TodoService todoService) {
        this.todoService = todoService;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        todoService.delete(id);
    }
}
