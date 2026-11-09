package com.example.demo.todo.application;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.todo.application.port.out.TodoRepositoryPort;
import com.example.demo.todo.domain.Todo;
import com.example.demo.todo.domain.TodoNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Todo 도메인 서비스.
 *
 * <p>유스케이스 계층이 호출하는 Todo 도메인의 실제 동작을 담당한다.
 * 아웃바운드 포트에만 의존하므로 JPA/H2에 대한 지식은 없고,
 * 값 검증 같은 불변식은 도메인 모델이 스스로 지킨다.
 *
 * <p><b>트랜잭션을 스스로 열지 않는다.</b> 경계는 유스케이스 구현체에 있고
 * 이 클래스는 {@link Propagation#MANDATORY} 로 호출자의 트랜잭션에 참여만 한다.
 * 트랜잭션 없이 직접 호출하면 예외가 나므로, 경계를 빠뜨린 코드가 조용히
 * 각자 커밋되는 사고를 막는다.
 *
 * <p>다른 도메인의 서비스를 호출하지 않는다. 도메인 간 조율은 유스케이스 구현체의 몫이다.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class TodoService {

    private final TodoRepositoryPort repository;
    private final Clock clock;

    public TodoService(TodoRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResult<Todo> findAll(Boolean completed, PageQuery pageQuery) {
        return (completed == null)
                ? repository.findAll(pageQuery)
                : repository.findByCompleted(completed, pageQuery);
    }

    public Todo findById(Long id) {
        return getOrThrow(id);
    }

    public Todo create(String title, String description, boolean completed) {
        Todo todo = Todo.create(title, description, completed, Instant.now(clock));
        return repository.save(todo);
    }

    public Todo update(Long id, String title, String description, boolean completed) {
        Todo todo = getOrThrow(id);
        todo.update(title, description, completed, Instant.now(clock));
        // 도메인 모델은 영속 컨텍스트와 분리돼 있으므로 변경 감지에 기대지 않고 명시적으로 저장한다.
        return repository.save(todo);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new TodoNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private Todo getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }
}
