package com.example.demo.usecase;

import com.example.demo.usecase.dto.TodoAppResponse;
import com.example.demo.usecase.dto.UpdateTodoAppRequest;

/**
 * Todo 수정 유스케이스.
 */
public interface UpdateTodoUseCase {

    TodoAppResponse update(UpdateTodoAppRequest request);
}
