package com.example.demo.todo.adapter.out.persistence;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.todo.application.port.out.TodoRepositoryPort;
import com.example.demo.todo.domain.Todo;
import com.example.demo.todo.domain.TodoNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 아웃바운드 어댑터 — {@link TodoRepositoryPort}의 JPA 구현.
 * JPA에 대한 지식이 이 클래스 바깥으로 새어 나가지 않는 것이 핵심이다.
 * 프레임워크 중립 타입({@link PageQuery})과 Spring Data 타입({@link Pageable}) 사이의
 * 변환도 여기서만 일어난다.
 */
@Component
public class TodoPersistenceAdapter implements TodoRepositoryPort {

    private static final Sort BY_ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    private final TodoJpaRepository jpaRepository;

    TodoPersistenceAdapter(TodoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Todo save(Todo todo) {
        TodoJpaEntity entity;
        if (todo.isNew()) {
            entity = TodoMapper.toNewEntity(todo);
        } else {
            // 기존 행을 읽어 변경분만 반영한다. createdAt 같은 불변 컬럼이 덮이지 않는다.
            entity = jpaRepository.findById(todo.getId())
                    .orElseThrow(() -> new TodoNotFoundException(todo.getId()));
            entity.applyChanges(
                    todo.getTitle(),
                    todo.getDescription(),
                    todo.isCompleted(),
                    todo.getUpdatedAt()
            );
        }
        return TodoMapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Todo> findById(Long id) {
        return jpaRepository.findById(id).map(TodoMapper::toDomain);
    }

    @Override
    public List<Todo> findAll() {
        return jpaRepository.findAllByOrderByIdAsc().stream()
                .map(TodoMapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<Todo> findAll(PageQuery pageQuery) {
        return toPageResult(jpaRepository.findAll(toPageable(pageQuery)), pageQuery);
    }

    @Override
    public PageResult<Todo> findByCompleted(boolean completed, PageQuery pageQuery) {
        return toPageResult(jpaRepository.findByCompleted(completed, toPageable(pageQuery)), pageQuery);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private Pageable toPageable(PageQuery pageQuery) {
        return PageRequest.of(pageQuery.page(), pageQuery.size(), BY_ID_ASC);
    }

    private PageResult<Todo> toPageResult(Page<TodoJpaEntity> page, PageQuery pageQuery) {
        return PageResult.of(
                page.getContent().stream().map(TodoMapper::toDomain).toList(),
                pageQuery.page(),
                pageQuery.size(),
                page.getTotalElements()
        );
    }
}
