# StablePay Java

Java 21 / Spring Boot 기반 로컬 ETH 지급 프로젝트.

- `api/`: 백엔드와 테스트
- `Source Code/`: TypeScript 참고 예제. Git 추적 대상에서 제외한다.
- Git 저장소는 루트 하나만 사용한다.

## 개발

```sh
cd api
./gradlew test
./gradlew bootRun
```

포팅은 작업별 `codex/` 브랜치에서 검증한 뒤 기본 브랜치에 머지한다.
기준선은 기존 Todo 애플리케이션이며 지급 기능은 순차적으로 추가한다.

## 로컬 인프라

PostgreSQL, Anvil, Kafka 실행 절차는 [로컬 개발 안내](docs/local-development.md)를 참고한다.

## 구현 상태

- 완료: 로컬 인프라, PostgreSQL/Flyway, ETH 지급 도메인, 지급 생성·조회 API
- 후속: Kafka 이벤트 연결, ETH 전송, 정산 확인, 장애 복구, 대사
- 지급 생성은 현재 DB/Outbox 저장까지 수행하며 실제 자금 전송은 아직 수행하지 않는다.

검증: `cd api && ./gradlew test postgresTest` (`postgresTest`는 로컬 PostgreSQL 필요).
