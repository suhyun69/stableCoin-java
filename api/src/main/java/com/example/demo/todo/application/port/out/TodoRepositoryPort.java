package com.example.demo.todo.application.port.out;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.todo.domain.Todo;

import java.util.List;
import java.util.Optional;

/**
 * 아웃바운드 포트 — 저장소에 요구하는 계약.
 *
 * <p>애플리케이션 계층이 정의하고 영속성 어댑터가 구현한다(의존성 역전).
 * 시그니처에 JPA 타입이 등장하지 않으므로 구현을 JPA 대신 JDBC, 외부 API,
 * 인메모리 가짜 객체로 바꿔도 애플리케이션 계층은 그대로다.
 * 페이징도 {@link PageQuery}/{@link PageResult} 라는 자체 타입으로 표현한다.
 */
public interface TodoRepositoryPort {

    Todo save(Todo todo);

    Optional<Todo> findById(Long id);

    /** 페이징 없는 전체 조회. API로 노출하지 않고 내부 작업(정리, 배치)에만 쓴다. */
    List<Todo> findAll();

    PageResult<Todo> findAll(PageQuery pageQuery);

    PageResult<Todo> findByCompleted(boolean completed, PageQuery pageQuery);

    boolean existsById(Long id);

    void deleteById(Long id);
}
