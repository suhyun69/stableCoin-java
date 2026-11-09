package com.example.demo.usecase;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.usecase.dto.TodoAppResponse;

/**
 * Todo 조회 유스케이스.
 */
public interface ReadTodoUseCase {

    /** completed 가 null 이면 전체 조회, 아니면 해당 상태만 조회한다. */
    PageResult<TodoAppResponse> readAll(Boolean completed, PageQuery pageQuery);

    TodoAppResponse readById(Long id);
}
