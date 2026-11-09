package com.example.demo.usecase;

import com.example.demo.todo.application.TodoService;
import com.example.demo.todo.application.port.out.TodoRepositoryPort;
import com.example.demo.usecase.dto.AddTodoAppRequest;
import com.example.demo.usecase.dto.TodoAppResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 트랜잭션 경계가 유스케이스 계층에 있는지 검증한다.
 *
 * <p>도메인 서비스는 {@code Propagation.MANDATORY} 라 스스로 트랜잭션을 열지 않는다.
 * 경계를 빠뜨린 호출은 조용히 각자 커밋되는 대신 예외로 드러난다.
 */
@SpringBootTest
class TransactionBoundaryTest {

    @Autowired
    private TodoService todoService;

    @Autowired
    private AddTodoUseCase addTodoUseCase;

    @Autowired
    private TodoRepositoryPort repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetStore() {
        repository.findAll().forEach(todo -> repository.deleteById(todo.getId()));
    }

    @Test
    @DisplayName("트랜잭션 없이 도메인 서비스를 직접 부르면 예외가 난다")
    void domainServiceRequiresCallerTransaction() {
        assertThatThrownBy(() -> todoService.create("경계 밖 호출", null, false))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("유스케이스를 통하면 경계가 열리므로 정상 동작한다")
    void useCaseOpensTransaction() {
        TodoAppResponse created = addTodoUseCase.add(new AddTodoAppRequest("정상 경로", null, false));

        assertThat(repository.findById(created.id())).isPresent();
    }

    @Test
    @DisplayName("한 경계 안의 여러 쓰기는 중간에 실패하면 함께 롤백된다")
    void writesInOneBoundaryRollBackTogether() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.execute(status -> {
            todoService.create("첫 번째", null, false);
            todoService.create("두 번째", null, false);
            // 유스케이스가 두 도메인 서비스를 호출하다 두 번째에서 실패하는 상황을 흉내낸다.
            throw new IllegalStateException("의도적 실패");
        })).isInstanceOf(IllegalStateException.class);

        // 경계가 도메인 서비스에 있었다면 첫 번째는 이미 커밋돼 남아 있었을 것이다.
        assertThat(repository.findAll()).isEmpty();
    }
}
