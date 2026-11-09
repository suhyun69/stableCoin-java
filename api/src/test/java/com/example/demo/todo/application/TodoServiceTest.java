package com.example.demo.todo.application;

import com.example.demo.common.page.PageQuery;
import com.example.demo.common.page.PageResult;
import com.example.demo.todo.application.port.out.TodoRepositoryPort;
import com.example.demo.todo.domain.Todo;
import com.example.demo.todo.domain.TodoNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 도메인 서비스 단위 테스트.
 *
 * <p>아웃바운드 포트를 인메모리 가짜 구현으로 갈아끼웠기 때문에 스프링 컨텍스트도,
 * H2도, JPA도 필요 없다. 페이징도 프레임워크 중립 타입이라 그대로 검증할 수 있다.
 */
class TodoServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private FakeTodoRepository repository;
    private TodoService service;

    @BeforeEach
    void setUp() {
        repository = new FakeTodoRepository();
        service = new TodoService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("생성하면 식별자가 채워져 반환된다")
    void create() {
        Todo created = service.create("할 일", "설명", false);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getTitle()).isEqualTo("할 일");
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(repository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("completed 필터가 동작한다")
    void filterByCompleted() {
        service.create("A", null, false);
        service.create("B", null, true);

        assertThat(service.findAll(null, PageQuery.first()).content()).hasSize(2);
        assertThat(service.findAll(true, PageQuery.first()).content())
                .extracting(Todo::getTitle).containsExactly("B");
        assertThat(service.findAll(false, PageQuery.first()).content())
                .extracting(Todo::getTitle).containsExactly("A");
    }

    @Test
    @DisplayName("페이지를 나눠 돌려주고 전체 개수를 함께 알려준다")
    void paginates() {
        for (int i = 1; i <= 5; i++) {
            service.create("할 일 " + i, null, false);
        }

        PageResult<Todo> firstPage = service.findAll(null, new PageQuery(0, 2));
        assertThat(firstPage.content()).extracting(Todo::getTitle)
                .containsExactly("할 일 1", "할 일 2");
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();

        PageResult<Todo> lastPage = service.findAll(null, new PageQuery(2, 2));
        assertThat(lastPage.content()).extracting(Todo::getTitle).containsExactly("할 일 5");
        assertThat(lastPage.isLast()).isTrue();

        PageResult<Todo> beyond = service.findAll(null, new PageQuery(9, 2));
        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("수정하면 저장소에 반영되고 createdAt은 보존된다")
    void update() {
        Todo created = service.create("원래", null, false);

        Todo updated = service.update(created.getId(), "바뀜", "새 설명", true);

        assertThat(updated.getTitle()).isEqualTo("바뀜");
        assertThat(updated.isCompleted()).isTrue();
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(repository.findById(created.getId()).orElseThrow().getTitle()).isEqualTo("바뀜");
    }

    @Test
    @DisplayName("없는 id를 조회/수정/삭제하면 TodoNotFoundException")
    void missingId() {
        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(TodoNotFoundException.class);

        assertThatThrownBy(() -> service.update(999L, "제목", null, false))
                .isInstanceOf(TodoNotFoundException.class);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(TodoNotFoundException.class);
    }

    @Test
    @DisplayName("삭제하면 저장소에서 사라진다")
    void delete() {
        Todo created = service.create("지울 항목", null, false);

        service.delete(created.getId());

        assertThat(repository.findById(created.getId())).isEmpty();
    }

    /**
     * 테스트 전용 아웃바운드 어댑터. 실제 영속성 어댑터와 같은 포트를 구현한다.
     */
    private static class FakeTodoRepository implements TodoRepositoryPort {

        private final Map<Long, Todo> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong(0);

        @Override
        public Todo save(Todo todo) {
            Long id = todo.isNew() ? sequence.incrementAndGet() : todo.getId();
            Todo persisted = Todo.reconstitute(
                    id,
                    todo.getTitle(),
                    todo.getDescription(),
                    todo.isCompleted(),
                    todo.getCreatedAt(),
                    todo.getUpdatedAt()
            );
            store.put(id, persisted);
            return persisted;
        }

        @Override
        public Optional<Todo> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Todo> findAll() {
            return store.values().stream()
                    .sorted(Comparator.comparing(Todo::getId))
                    .toList();
        }

        @Override
        public PageResult<Todo> findAll(PageQuery pageQuery) {
            return slice(findAll(), pageQuery);
        }

        @Override
        public PageResult<Todo> findByCompleted(boolean completed, PageQuery pageQuery) {
            List<Todo> matched = findAll().stream()
                    .filter(todo -> todo.isCompleted() == completed)
                    .toList();
            return slice(matched, pageQuery);
        }

        private PageResult<Todo> slice(List<Todo> all, PageQuery pageQuery) {
            int from = Math.min(pageQuery.page() * pageQuery.size(), all.size());
            int to = Math.min(from + pageQuery.size(), all.size());
            return PageResult.of(all.subList(from, to), pageQuery.page(), pageQuery.size(), all.size());
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }
    }
}
