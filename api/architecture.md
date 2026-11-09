# Architecture

이 프로젝트의 기본 구조를 설명한다. 새 기능이나 새 도메인을 추가할 때는 이 문서의 규칙을 따른다.

- **기술 스택** — Spring Boot 3.5.16 / Java 21 (toolchain) / Gradle 9.2.1 (래퍼 포함)
- **저장소** — H2. 개발용은 파일 기반(`./data/demo`)이라 재시작해도 데이터가 남고, 테스트는 인메모리를 쓴다.
- **API 문서** — springdoc-openapi. 실행 후 `http://localhost:8080/swagger-ui.html` (스펙 원본은 `/v3/api-docs`)
- **빌드** — `./gradlew build`, **실행** — `./gradlew bootRun`

---

## 1. 한눈에 보기

요청은 네 겹의 경계를 지난다. **경계마다 전용 타입이 있고, 한 계층의 타입이 다음 경계를 그대로 넘지 않는다.**

```
HTTP 요청
   │
   ▼
┌─────────────────────────────────────────────────────────┐
│ controller/            TodoWebRequest  ──┐              │  웹 계층
│                        TodoWebResponse ◄─┼──┐           │  HTTP만 안다
└──────────────────────────────────────────┼──┼───────────┘
                                           ▼  │
┌─────────────────────────────────────────────┼───────────┐
│ usecase/               AddTodoAppRequest ──┐│           │  유스케이스 계층
│   ├── (interface)      TodoAppResponse ────┼┘           │  흐름을 조율한다
│   └── impl/                                │            │
└─────────────────────────────────────────────┼───────────┘
                                             ▼
┌─────────────────────────────────────────────────────────┐
│ todo/application/      TodoService                      │  도메인 서비스
│ todo/domain/           Todo (순수 자바)                  │  규칙을 지킨다
└──────────────────────┬──────────────────────────────────┘
                       │ TodoRepositoryPort (인터페이스)
                       ▼
┌─────────────────────────────────────────────────────────┐
│ todo/adapter/out/persistence/   JPA, H2                 │  영속성 어댑터
└─────────────────────────────────────────────────────────┘
```

데이터 변환 순서:

```
TodoWebRequest → AddTodoAppRequest → Todo → TodoAppResponse → TodoWebResponse
   (웹 입력)        (유스케이스 입력)   (도메인)   (유스케이스 출력)   (웹 출력)
```

---

## 2. 패키지 구조

```
com.example.demo
├── DemoApplication.java
│
├── controller/                          ← 웹 계층 (인바운드 어댑터)
│   ├── TodoController.java              유스케이스 인터페이스에만 의존
│   ├── HelloController.java             헬스체크용 (규칙 예외, 4장 참고)
│   └── dto/
│       ├── TodoWebRequest.java          @RequestBody + Bean Validation
│       ├── TodoWebResponse.java         HTTP 응답 본문
│       └── PageWebResponse.java         페이지 응답 (제네릭)
│
├── usecase/                             ← 유스케이스 계층
│   ├── AddTodoUseCase.java              TodoAppResponse add(AddTodoAppRequest)
│   ├── ReadTodoUseCase.java             readAll(Boolean) / readById(Long)
│   ├── UpdateTodoUseCase.java           TodoAppResponse update(UpdateTodoAppRequest)
│   ├── DeleteTodoUseCase.java           void delete(Long)
│   ├── dto/
│   │   ├── AddTodoAppRequest.java
│   │   ├── UpdateTodoAppRequest.java
│   │   └── TodoAppResponse.java         from(Todo) — 도메인이 여기서 멈춘다
│   └── impl/
│       ├── AddTodoUseCaseImpl.java      도메인 서비스 호출 + AppResponse 변환
│       ├── ReadTodoUseCaseImpl.java     ★ 트랜잭션 경계가 이 계층에 있다
│       ├── UpdateTodoUseCaseImpl.java
│       └── DeleteTodoUseCaseImpl.java
│
├── todo/                                ← Todo 도메인 (헥사고날)
│   ├── domain/
│   │   ├── Todo.java                    프레임워크 import 0건, 불변식 자체 검증
│   │   └── TodoNotFoundException.java   common의 NotFoundException 상속
│   ├── application/
│   │   ├── TodoService.java             도메인 서비스 (MANDATORY — 경계를 열지 않는다)
│   │   └── port/out/
│   │       └── TodoRepositoryPort.java  아웃바운드 포트 (애플리케이션이 정의)
│   └── adapter/out/persistence/         아웃바운드 어댑터 (JPA 구현)
│       ├── TodoPersistenceAdapter.java  포트 구현
│       ├── TodoJpaEntity.java           영속성 전용 모델
│       ├── TodoJpaRepository.java       Spring Data (package-private)
│       └── TodoMapper.java              도메인 ↔ 엔티티 변환
│
├── common/                              ← 도메인에 종속되지 않는 공통 기반
│   ├── exception/
│   │   ├── BusinessException.java       모든 업무 예외의 최상위 (abstract)
│   │   ├── ErrorCode.java               NOT_FOUND / INVALID_INPUT / CONFLICT / INTERNAL_ERROR
│   │   ├── NotFoundException.java
│   │   └── InvalidInputException.java
│   ├── page/
│   │   ├── PageQuery.java               페이지 요청 (프레임워크 중립)
│   │   └── PageResult.java              페이지 결과 (프레임워크 중립)
│   └── web/
│       ├── GlobalExceptionHandler.java  예외 → HTTP 번역이 일어나는 유일한 지점
│       └── ErrorResponse.java
│
└── config/
    ├── AppConfig.java                   Clock 빈 (테스트에서 시각 고정용)
    └── OpenApiConfig.java               OpenAPI 문서 메타데이터
```

---

## 3. 각 계층의 책임

### controller/ — 웹 계층

HTTP를 유스케이스 호출로 번역하는 일만 한다.

- **한다** — 요청 본문 바인딩, Bean Validation(`@Valid`), 상태 코드와 `Location` 헤더 결정, WebRequest ↔ AppRequest/AppResponse 변환
- **하지 않는다** — 업무 규칙 판단, 트랜잭션 관리, 도메인 모델 직접 사용

`TodoController`는 유스케이스 인터페이스 네 개만 주입받는다. 구현체(`*UseCaseImpl`)도 `TodoService`도 알지 못한다.

### usecase/ — 유스케이스 계층

"무엇을 할 수 있는가"를 인터페이스로 드러내고, 구현체가 그 흐름을 조율한다.

- **인터페이스** — 유스케이스 단위로 하나씩. 호출자(컨트롤러, 배치, 메시지 컨슈머)는 이것만 본다.
- **구현체** — 도메인 서비스를 호출하고, 돌아온 도메인 모델을 `~AppResponse`로 변환해 내보낸다. **트랜잭션 경계와 도메인 간 조율이 여기 있다.**

지금은 Todo 하나뿐이라 구현체가 얇지만, 이 계층을 둔 이유는 **여러 도메인에 걸친 흐름을 조율할 자리**를 확보하기 위해서다. 예를 들어 "할 일을 완료하면 포인트를 적립한다"면 `CompleteTodoUseCaseImpl`이 `TodoService`와 `PointService`를 함께 부른다. 자세한 규칙은 6장을 참고한다.

### todo/ — 도메인

헥사고날(포트/어댑터) 구조를 따른다.

- `domain/Todo` — 프레임워크 의존이 전혀 없다. `title` 필수, 길이 제한 같은 불변식을 생성자와 `update()`에서 스스로 검증하고, 위반 시 `InvalidInputException`을 던진다.
- `application/TodoService` — 유스케이스가 요청한 동작을 수행한다. 아웃바운드 포트에만 의존하므로 JPA를 모른다. **트랜잭션을 스스로 열지 않고**(`Propagation.MANDATORY`) 호출자의 것에 참여만 한다. 다른 도메인의 서비스도 호출하지 않는다.
- `application/port/out/TodoRepositoryPort` — **애플리케이션이 정의하고 어댑터가 구현한다**(의존성 역전). 시그니처에 JPA 타입이 없어서 구현을 JDBC나 외부 API, 인메모리 가짜 객체로 바꿔도 위 계층은 그대로다.
- `adapter/out/persistence` — 포트의 JPA 구현. 도메인 `Todo`와 `TodoJpaEntity`를 분리하고 `TodoMapper`로 변환한다.

### common/ — 공통 기반

**개별 도메인을 참조하지 않는다.** `common`에서 `com.example.demo.todo`를 import하면 규칙 위반이다.

---

## 4. 네이밍 규칙

| 경계 | 접미사 | 위치 | 예시 |
|---|---|---|---|
| 웹 입력 | `~WebRequest` | `controller/dto` | `TodoWebRequest` |
| 웹 출력 | `~WebResponse` | `controller/dto` | `TodoWebResponse` |
| 유스케이스 입력 | `~AppRequest` | `usecase/dto` | `AddTodoAppRequest`, `UpdateTodoAppRequest` |
| 유스케이스 출력 | `~AppResponse` | `usecase/dto` | `TodoAppResponse` |
| 유스케이스 | `~UseCase` / `~UseCaseImpl` | `usecase`, `usecase/impl` | `AddTodoUseCase` |
| 아웃바운드 포트 | `~Port` | `*/application/port/out` | `TodoRepositoryPort` |
| 아웃바운드 어댑터 | `~Adapter` | `*/adapter/out/*` | `TodoPersistenceAdapter` |

DTO는 모두 `record`로 만든다.

**예외 두 가지.**

1. `HelloController` 같은 헬스체크/데모용 엔드포인트는 `Map<String, String>` 반환을 그대로 둔다. 도메인이 없는 엔드포인트까지 전용 타입을 만들 필요는 없다고 판단했다.
2. 식별자 하나만 받는 `readById(Long)` / `delete(Long)`는 `~AppRequest`로 감싸지 않는다. 본문이 없는 요청까지 래퍼를 만들면 이름만 늘어난다.

---

## 5. 의존 규칙

화살표 방향으로만 의존한다. 반대 방향 import가 생기면 설계가 깨진 것이다.

```
controller ──► usecase ──► todo/application ──► todo/domain
                                 ▲
                                 │ (포트 구현, 의존성 역전)
                     todo/adapter/out/persistence

common ◄── 누구나 참조 가능 / common은 아무 도메인도 참조하지 않음
```

### 규칙은 테스트가 강제한다

이 장의 규칙은 문서상의 합의가 아니라 **실행되는 테스트**다. `ArchitectureTest`가 컴파일된 클래스의 의존 그래프를 읽어 검사하고, 어기면 빌드가 실패한다.

```bash
./gradlew test --tests 'com.example.demo.ArchitectureTest'
```

| # | 규칙 | 뜻 |
|---|---|---|
| 1 | `계층_의존_방향` | 컨트롤러→유스케이스→도메인서비스 방향으로만 접근 |
| 2 | `도메인은_프레임워크를_모른다` | `..domain..`에 jakarta/spring/hibernate 의존 없음 |
| 3 | `도메인서비스는_JPA를_모른다` | `..application..`에 영속성 기술 의존 없음 |
| 4 | `컨트롤러는_도메인모델을_모른다` | 웹 계층은 `~AppResponse`까지만 본다 |
| 5 | `common은_도메인을_모른다` | 공통 기반이 개별 도메인에 종속되지 않음 |
| 6 | `컨트롤러는_유스케이스_구현체를_모른다` | 인터페이스에만 의존 |
| 7 | `도메인서비스는_다른_도메인을_모른다` | 6장 규칙 1 |
| 8–12 | `*_네이밍` | 4장 네이밍 규칙 (웹/앱 DTO, 유스케이스, 포트) |
| 13 | `도메인서비스는_트랜잭션을_열지_않는다` | `MANDATORY` 확인 |
| 14 | `유스케이스_구현체는_트랜잭션_경계다` | `@Transactional` 확인 |

위반하면 파일과 줄 번호까지 나온다.

```
Rule 'no classes that reside in a package 'com.example.demo.controller..'
should depend on classes that reside in a package '..domain..'' was violated (2 times):
Method <...TempBadController.titleOf(...Todo)> has parameter of type <...todo.domain.Todo>
```

**7번 규칙은 도메인이 늘어도 고치지 않는다.** 클래스의 패키지에서 도메인 이름을 뽑아 비교하는 방식이라, `order`를 추가하면 자동으로 검사 대상에 들어온다. 다만 도메인이 아닌 기반 패키지를 새로 만들면 `ArchitectureTest.NON_DOMAIN_PACKAGES`에 등록해야 한다.

`todo/application`에 남아 있는 스프링 import는 `@Service`, `@Transactional`, `Propagation` 셋뿐이다.

---

## 6. 도메인 간 협력과 트랜잭션

도메인이 여러 개로 늘어날 때를 위한 규칙이다. **지금 지키지 않으면 나중에 되돌리기 어렵다.**

### 규칙 1 — 도메인 서비스끼리 직접 호출하지 않는다

```java
// ❌ OrderService 가 TodoService 를 주입받는다
public class OrderService {
    private final TodoService todoService;   // 도메인 간 의존이 그래프로 엉킨다
}

// ✅ 유스케이스 구현체가 둘 다 호출한다
@Service
public class CompleteOrderUseCaseImpl implements CompleteOrderUseCase {
    private final OrderService orderService;
    private final TodoService todoService;

    @Override
    @Transactional
    public OrderAppResponse complete(CompleteOrderAppRequest request) {
        Order order = orderService.complete(request.orderId());
        todoService.create("배송 확인: " + order.getId(), null, false);
        return OrderAppResponse.from(order);
    }
}
```

도메인 서비스는 끝까지 자기 도메인만 안다. 도메인 간 흐름은 전부 `usecase/impl/` 한 곳에 모이므로, "무엇이 무엇을 부르는지"를 그 디렉터리만 읽으면 파악할 수 있다.

### 규칙 2 — 트랜잭션 경계는 유스케이스 구현체에 있다

| 계층 | 선언 | 역할 |
|---|---|---|
| `*UseCaseImpl` | `@Transactional` (조회는 `readOnly = true`) | **경계를 연다** |
| `*Service` (도메인) | `@Transactional(propagation = MANDATORY)` | 호출자의 트랜잭션에 **참여만 한다** |

도메인 서비스에 `MANDATORY`를 건 이유는 경계를 빠뜨린 호출을 조용히 넘기지 않기 위해서다. 기본값(`REQUIRED`)이면 유스케이스를 거치지 않은 호출도 각자 트랜잭션을 열어 **개별 커밋**되고, 두 번째 도메인 작업이 실패해도 첫 번째는 그대로 남는다. 이 증상은 도메인이 하나일 때는 드러나지 않다가 둘째가 붙는 날 데이터 정합성 문제로 터진다.

`MANDATORY`면 트랜잭션 없이 도메인 서비스를 부르는 순간 `IllegalTransactionStateException`이 난다. 실수가 런타임에 바로 드러난다.

이 두 성질은 `usecase/TransactionBoundaryTest`가 검증한다.

- 트랜잭션 없이 `todoService.create(...)` → 예외
- 유스케이스를 통한 호출 → 정상
- 한 경계 안의 두 쓰기 중간에 실패 → **둘 다 롤백**

---

## 7. 예외 처리

도메인은 HTTP를 모르고, HTTP 번역은 한 곳에서만 일어난다.

```
TodoNotFoundException  extends  NotFoundException  extends  BusinessException
                                                               │ ErrorCode
                                                               ▼
                                              GlobalExceptionHandler
                                                   ErrorCode → HttpStatus
```

`GlobalExceptionHandler`는 개별 도메인 예외를 알지 못하고 `BusinessException` 하나만 처리한다. **새 도메인이 늘어도 이 파일은 고치지 않는다.**

| 상황 | 응답 |
|---|---|
| `BusinessException` (ErrorCode에 따라) | 404 / 400 / 409 / 500 |
| `@Valid` 실패 | 400 + `fieldErrors` |
| 깨진 JSON 본문 | 400 |
| 경로 변수 타입 불일치 | 400 |
| 그 외 모든 예외 | 500 (내부 메시지 숨기고 서버에만 로깅) |

응답 본문 형식:

```json
{
  "timestamp": "2026-11-05T05:51:09.986249Z",
  "status": 404,
  "error": "Not Found",
  "message": "Todo를 찾을 수 없습니다. id=99999",
  "fieldErrors": { "title": "title은 필수입니다." }
}
```

`fieldErrors`는 값이 있을 때만 직렬화된다.

**검증은 두 겹이다.** `~WebRequest`의 Bean Validation이 HTTP 입구에서 형식을 거르고, 도메인 모델이 불변식을 다시 검사한다. 지금은 두 제약이 같아서(`@Size(max=200)` ↔ `MAX_TITLE_LENGTH`) 도메인 검증이 HTTP 경로로는 도달하지 않지만, 배치나 메시지 컨슈머처럼 웹을 거치지 않는 호출자를 위한 2차 방어선이다.

---

## 8. 페이지네이션과 API 문서

### 페이지네이션

목록 조회는 페이지 단위다. `GET /api/todos?page=0&size=20`

```json
{
  "content": [ { "id": 1, "title": "할 일 1", ... } ],
  "page": 0, "size": 2, "totalElements": 5, "totalPages": 3,
  "first": true, "last": false
}
```

기본값은 `page=0`, `size=20`이고 `size`는 1~100이다. 범위를 벗어나면 `400`이 난다. 정렬은 `id` 오름차순 고정이다.

**Spring Data 의 `Pageable`/`Page` 를 쓰지 않는다.** 그 타입들은 `org.springframework.data` 패키지라서 도메인 서비스가 참조하는 순간 `ArchitectureTest.도메인서비스는_JPA를_모른다` 에 걸린다. 대신 프레임워크 중립 타입을 `common/page/` 에 두고, Spring Data 타입으로의 변환은 영속성 어댑터에서만 한다.

```
PageQuery            (common/page)   page, size — 생성자에서 범위 검증
    ↓ 포트 → 도메인 서비스 → 유스케이스까지 그대로 전달
PageRequest/Pageable (adapter)       ← 어댑터에서만 변환
    ↓
Page<TodoJpaEntity>  (adapter)
    ↓ toPageResult()
PageResult<Todo>     (common/page)   map(TodoAppResponse::from) 으로 내용물만 교체
PageResult<TodoAppResponse>
    ↓ PageWebResponse.from(result, TodoWebResponse::from)
PageWebResponse<TodoWebResponse>     (controller/dto)
```

`PageResult.map()` 덕분에 계층을 지날 때 페이지 정보는 그대로 두고 내용물 타입만 바꾼다.

포트에는 페이징 없는 `findAll()` 도 남아 있다. API로 노출하지 않고 정리·배치 같은 내부 작업에만 쓴다.

### API 문서

springdoc-openapi 가 컨트롤러와 DTO를 읽어 OpenAPI 스펙을 생성한다. 별도 문서를 손으로 유지하지 않는다.

| 경로 | 내용 |
|---|---|
| `/swagger-ui.html` | 브라우저에서 보고 직접 호출 |
| `/v3/api-docs` | OpenAPI 3 스펙 (JSON) |

**애너테이션은 웹 계층에만 붙인다.** `@Tag`, `@Operation`, `@ApiResponse` 는 컨트롤러에, `@Schema` 는 `controller/dto` 에만 둔다. 유스케이스 DTO와 도메인은 문서화 도구도 모른다 — 문서화는 HTTP 표현의 관심사이기 때문이다.

---

## 9. 영속성

- 도메인 `Todo`와 `TodoJpaEntity`는 **별도 클래스**다. 테이블 구조가 바뀌어도 매퍼만 고치면 된다.
- 그 대가로 **변경 감지(dirty checking)를 쓰지 않는다.** 도메인 모델은 영속 컨텍스트가 관리하는 객체가 아니므로, 수정 후 `repository.save()`를 명시적으로 호출한다.
- 어댑터의 `save()`는 기존 행을 읽어 변경분만 반영한다. `createdAt` 같은 불변 컬럼이 덮이지 않는다.
- 스키마는 `ddl-auto: update`로 생성된다. 운영에 쓸 거라면 Flyway/Liquibase로 옮겨야 한다.

```sql
create table todo (
    id bigint generated by default as identity,
    completed boolean not null,
    created_at timestamp(6) with time zone not null,
    description varchar(2000),
    title varchar(200) not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id)
)
```

`http://localhost:8080/h2-console`에서 `jdbc:h2:file:./data/demo`로 접속해 데이터를 직접 볼 수 있다.

---

## 10. 테스트 전략

계층이 분리된 만큼 테스트도 필요한 만큼만 띄운다. **총 46건.**

| 테스트 | 건수 | 띄우는 것 | 보는 것 |
|---|---|---|---|
| `ArchitectureTest` | 14 | 없음 (클래스 파일 분석) | 이 문서의 구조 규칙 |
| `todo/domain/TodoTest` | 4 | 없음 (순수 자바) | 도메인 불변식 |
| `usecase/TodoUseCaseTest` | 4 | 없음 (Mockito) | 구현체 → 서비스 위임, AppResponse 변환 |
| `todo/application/TodoServiceTest` | 6 | 없음 (포트 가짜 구현) | 도메인 서비스 흐름, 페이징 |
| `usecase/TransactionBoundaryTest` | 3 | 스프링 + H2 | 트랜잭션 경계와 롤백 |
| `controller/TodoControllerTest` | 8 | 스프링 + H2 | 전 구간 통합, 페이징 |
| `common/web/GlobalExceptionHandlerTest` | 5 | 스프링 + H2 | 에러 응답 |
| `DemoApplicationTests` | 2 | 스프링 | 컨텍스트 로딩, `/hello` |

`TodoServiceTest`가 DB 없이 도는 것은 아웃바운드 포트를 인메모리 가짜 구현으로 갈아끼울 수 있기 때문이고, 이것이 포트/어댑터 구조에서 얻는 가장 직접적인 이득이다.

테스트는 `src/test/resources/application.yml`의 인메모리 H2를 쓰므로 개발용 `./data`를 건드리지 않는다.

---

## 11. 새 도메인을 추가하는 순서

`order` 도메인을 예로 들면:

1. **도메인** — `order/domain/Order.java` (프레임워크 import 없이, 불변식은 스스로 검증). 필요하면 `OrderNotFoundException extends NotFoundException`.
2. **아웃바운드 포트** — `order/application/port/out/OrderRepositoryPort.java`
3. **도메인 서비스** — `order/application/OrderService.java` (`@Service`, `@Transactional(propagation = MANDATORY)`, 포트에만 의존). **다른 도메인 서비스를 주입받지 않는다.**
4. **영속성 어댑터** — `order/adapter/out/persistence/`에 `OrderJpaEntity`, `OrderJpaRepository`, `OrderMapper`, `OrderPersistenceAdapter`
5. **유스케이스 DTO** — `usecase/dto/`에 `AddOrderAppRequest`, `OrderAppResponse` (+ `from(Order)`)
6. **유스케이스** — `usecase/AddOrderUseCase.java`와 `usecase/impl/AddOrderUseCaseImpl.java`. 구현체에 `@Transactional`(조회는 `readOnly = true`)을 붙인다 — **경계는 여기다.**
7. **웹 DTO** — `controller/dto/`에 `OrderWebRequest`(+`toAddAppRequest()`), `OrderWebResponse`(+`from(OrderAppResponse)`)
8. **컨트롤러** — `controller/OrderController.java` (유스케이스 인터페이스만 주입)
9. **테스트** — 도메인 / 유스케이스 / 서비스 / 통합 네 갈래
10. **점검** — `./gradlew test`. `ArchitectureTest`가 구조 규칙을 자동으로 검사한다

`GlobalExceptionHandler`는 건드리지 않는다. `BusinessException`을 상속했다면 이미 처리된다.

두 도메인에 걸친 흐름이 생기면 **도메인 서비스끼리 연결하지 말고** 새 유스케이스 구현체를 만들어 거기서 둘을 호출한다(6장).

---

## 12. 설계 결정과 트레이드오프

이 구조는 공짜가 아니다. 무엇을 내주고 무엇을 얻었는지 적어 둔다.

**도메인 모델과 JPA 엔티티를 분리했다.**
→ 얻은 것: 도메인이 ORM에서 자유롭다. 테이블 구조가 바뀌어도 매퍼만 고친다.
→ 내준 것: 변경 감지를 못 쓰고, 수정 시 기존 행을 읽는 쿼리가 한 번 더 나간다. 매핑 코드도 늘어난다.

**유스케이스 계층을 따로 두었다.**
→ 얻은 것: 여러 도메인에 걸친 흐름을 조율할 자리가 생겼다. 컨트롤러가 도메인 서비스에 직접 묶이지 않는다.
→ 내준 것: 도메인이 하나뿐인 지금은 구현체가 위임만 하는 얇은 층이다.

**경계마다 DTO를 따로 두었다.**
→ 얻은 것: 도메인 필드가 바뀌어도 API 응답이 함께 흔들리지 않는다. 타입 이름만 봐도 어느 경계의 것인지 안다.
→ 내준 것: 필드 하나를 추가하려면 `~WebRequest`, `~AppRequest`, 도메인, `~AppResponse`, `~WebResponse`를 모두 고쳐야 한다.

**페이징 타입을 Spring Data 대신 직접 만들었다.**
→ 얻은 것: 도메인/유스케이스 계층이 영속성 기술에서 자유롭다. `PageResult.map()` 으로 계층 간 변환이 간단하다.
→ 내준 것: `PageQuery`/`PageResult` 를 유지해야 하고, 어댑터에 변환 코드가 는다. Spring Data 의 정렬·슬라이스 기능을 그대로 쓰지 못한다.

**유스케이스 인터페이스를 동작 단위로 쪼갰다.**
→ 현재는 Add/Read/Update/Delete 넷이다. 더 엄격한 변형은 메서드 하나당 인터페이스 하나를 두는 것이고, 유스케이스가 늘어 컨트롤러 생성자가 비대해지면 그때 쪼갠다.

**트랜잭션 경계를 유스케이스로 올리고, 도메인 서비스는 `MANDATORY`로 묶었다.**
→ 얻은 것: 여러 도메인 서비스를 호출해도 한 트랜잭션으로 묶인다. 경계를 빠뜨린 호출이 조용히 개별 커밋되지 않고 예외로 드러난다.
→ 내준 것: 도메인 서비스를 트랜잭션 없이 단독 호출할 수 없다. 스프링 컨텍스트에서 `TodoService`를 직접 쓰려면 호출자가 경계를 열어야 한다(단위 테스트는 프록시를 거치지 않아 영향 없음).

**도메인 서비스끼리 직접 호출을 금지했다.**
→ 얻은 것: 도메인 간 의존이 그래프로 엉키지 않는다. "무엇이 무엇을 부르는지"가 `usecase/impl/` 한 디렉터리에 모인다.
→ 내준 것: 두 도메인을 엮는 사소한 작업에도 유스케이스를 하나 만들어야 한다.

---

## 13. 아직 하지 않은 것

연습 프로젝트라 의도적으로 비워 둔 부분이다.

- 인증/인가
- DB 마이그레이션 도구 (`ddl-auto: update`에 의존 중)
- 낙관적 락 (`@Version`) — 동시 수정 시 나중 쓰기가 이긴다
- 정렬 옵션 — 목록 조회는 `id` 오름차순 고정이다
- 커서 기반 페이징 — 지금은 오프셋 방식이라 뒤쪽 페이지에서 느려진다
- 도메인별 패키지 분리 — `usecase/`와 `controller/dto/`는 아직 평평하다. 도메인이 둘이 되는 시점에 `usecase/todo/`, `usecase/order/`로 나눈다(보류 중인 결정).
