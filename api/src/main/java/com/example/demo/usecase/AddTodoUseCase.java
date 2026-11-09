package com.example.demo.usecase;

import com.example.demo.usecase.dto.AddTodoAppRequest;
import com.example.demo.usecase.dto.TodoAppResponse;

/**
 * Todo 등록 유스케이스. 컨트롤러는 이 인터페이스에만 의존하고 구현체는 알지 못한다.
 */
public interface AddTodoUseCase {

    TodoAppResponse add(AddTodoAppRequest request);
}
