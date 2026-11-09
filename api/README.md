# springboot-hexagonal-template

Spring Boot 헥사고날(포트/어댑터) 아키텍처 템플릿입니다. Todo CRUD를 예제 도메인으로 담고 있고, 계층 의존 방향·네이밍·트랜잭션 경계 같은 구조 규칙을 [ArchUnit](https://www.archunit.org/) 테스트로 강제합니다. 규칙을 어기면 빌드가 실패합니다.

구조와 설계 근거는 [architecture.md](architecture.md)에 자세히 정리되어 있습니다.

## 특징

- **경계마다 전용 타입** — `~WebRequest → ~AppRequest → 도메인 → ~AppResponse → ~WebResponse` 순서로 변환하며, 한 계층의 타입이 다음 경계를 그대로 넘지 않습니다.
- **프레임워크를 모르는 도메인** — 도메인 모델은 순수 자바이고, JPA 엔티티는 어댑터에 따로 둡니다.
- **유스케이스가 트랜잭션 경계** — 도메인 서비스는 `Propagation.MANDATORY`로 호출자의 트랜잭션에 참여만 하므로, 경계를 빠뜨린 호출은 바로 예외가 납니다.
- **예외 번역은 한 곳에서** — `BusinessException`을 상속하면 `GlobalExceptionHandler`를 고치지 않아도 HTTP 응답으로 변환됩니다.
- **프레임워크 중립 페이지네이션** — Spring Data의 `Pageable` 대신 `PageQuery`/`PageResult`를 쓰고, 변환은 어댑터에서만 합니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 | Java 21 (Gradle toolchain) |
| 프레임워크 | Spring Boot 3.5.16 (Web, Validation, Data JPA) |
| 빌드 | Gradle 9.2.1 (래퍼 포함) |
| 데이터베이스 | H2 (개발: 파일 기반, 테스트: 인메모리) |
| API 문서 | springdoc-openapi 2.8.17 |
| 테스트 | JUnit 5, Mockito, ArchUnit 1.5.1 |

## 시작하기

JDK 21이 필요합니다. 별도의 데이터베이스 설치는 필요 없습니다.

```bash
./gradlew bootRun
```

서버가 뜨면 아래 주소로 접속할 수 있습니다.

| 주소 | 내용 |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI 스펙 (JSON) |
| http://localhost:8080/h2-console | H2 콘솔 (JDBC URL `jdbc:h2:file:./data/demo;AUTO_SERVER=TRUE`, 사용자 `sa`, 비밀번호 없음) |

데이터는 `./data/demo.mv.db`에 저장되어 재시작해도 남습니다. 매번 초기화하려면 `application.yml`의 URL을 `jdbc:h2:mem:demo`로 바꿉니다.

## API

| 메서드 | 경로 | 설명 | 성공 응답 |
|---|---|---|---|
| `GET` | `/api/todos` | 목록 조회 (`completed`, `page`, `size`) | `200` |
| `GET` | `/api/todos/{id}` | 단건 조회 | `200` |
| `POST` | `/api/todos` | 등록 (`Location` 헤더 포함) | `201` |
| `PUT` | `/api/todos/{id}` | 전체 교체 (`completed`를 생략하면 `false`) | `200` |
| `DELETE` | `/api/todos/{id}` | 삭제 | `204` |
| `GET` | `/` | 상태 확인 | `200` |
| `GET` | `/hello` | 인사 (`name`, 기본값 `world`) | `200` |

요청 본문의 `title`은 필수이고 200자 이하, `description`은 2000자 이하입니다. 목록의 `page` 기본값은 `0`, `size` 기본값은 `20`(범위 1~100)이며 `id` 오름차순으로 정렬됩니다.

```bash
curl -i -X POST http://localhost:8080/api/todos \
  -H 'Content-Type: application/json' \
  -d '{"title": "우유 사기", "description": "2L 저지방"}'
```

```bash
curl 'http://localhost:8080/api/todos?completed=false&page=0&size=20'
```

오류 응답은 모두 같은 형식이고, `fieldErrors`는 검증에 실패했을 때만 포함됩니다.

```json
{
  "timestamp": "2026-11-05T05:51:09.986249Z",
  "status": 400,
  "error": "Bad Request",
  "message": "요청 값이 올바르지 않습니다.",
  "fieldErrors": { "title": "title은 필수입니다." }
}
```

## 프로젝트 구조

```
src/main/java/com/example/demo
├── controller/                  웹 계층 (인바운드 어댑터)
├── usecase/                     유스케이스 인터페이스, 앱 DTO
│   └── impl/                    구현체 — 트랜잭션 경계, 도메인 간 조율
├── todo/                        Todo 도메인
│   ├── domain/                  순수 자바 모델
│   ├── application/             도메인 서비스
│   │   └── port/out/            아웃바운드 포트
│   └── adapter/out/persistence/ JPA 구현
├── common/                      예외, 페이지, 웹 공통 기반 (도메인 참조 금지)
└── config/                      Clock 빈, OpenAPI 설정
```

의존은 화살표 방향으로만 흐릅니다.

```
controller ──► usecase ──► todo/application ──► todo/domain
                                 ▲
                                 │ (포트 구현, 의존성 역전)
                     todo/adapter/out/persistence
```

## 테스트

```bash
./gradlew test
```

구조 규칙만 따로 검사할 수도 있습니다.

```bash
./gradlew test --tests 'com.example.demo.ArchitectureTest'
```

테스트는 인메모리 H2를 쓰므로 개발용 `./data`를 건드리지 않습니다. 계층별 테스트 구성은 [architecture.md의 10장](architecture.md#10-테스트-전략)을 참고하세요.

## 템플릿으로 사용하기

1. `settings.gradle`의 `rootProject.name`과 `build.gradle`의 `group`을 바꿉니다.
2. `com.example.demo` 패키지 이름을 바꿉니다. `ArchitectureTest`의 `packages`와 `ROOT` 값, `application.yml`의 `logging.level` 키도 문자열이라 함께 바꿔야 합니다.
3. `application.yml`의 `spring.application.name`과 데이터소스 URL을 프로젝트에 맞게 바꿉니다.
4. `todo` 도메인을 참고해 새 도메인을 추가합니다. 순서는 [architecture.md의 11장](architecture.md#11-새-도메인을-추가하는-순서)에 있습니다.
5. 도메인이 아닌 기반 패키지를 새로 만들면 `ArchitectureTest.NON_DOMAIN_PACKAGES`에 등록합니다.
6. `./gradlew test`로 구조 규칙을 확인합니다.

## 운영 전에 채워야 할 것

연습용 템플릿이라 다음 항목은 의도적으로 비워 두었습니다.

- 인증/인가 (H2 콘솔과 Swagger UI도 열려 있습니다)
- DB 마이그레이션 도구 (지금은 `ddl-auto: update`에 의존합니다)
- 낙관적 락 (동시 수정 시 나중 쓰기가 이깁니다)
- 정렬 옵션과 커서 기반 페이징

자세한 내용은 [architecture.md의 13장](architecture.md#13-아직-하지-않은-것)을 참고하세요.
