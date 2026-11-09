# Architecture

Java 21 / Spring Boot / PostgreSQL JDBC / Kafka / Web3j 기반 ETH 지급 서비스.

## 경계

- `controller`: HTTP 바인딩·검증·Web DTO 변환. 유스케이스 인터페이스만 호출한다.
- `runtime`: Kafka listener와 스케줄러. 유스케이스 인터페이스만 호출한다.
- `usecase`: 도메인 간 조율과 트랜잭션 경계. 입력·출력은 App DTO 또는 단순 식별자다.
- `<domain>/domain`: 프레임워크에 의존하지 않는 규칙과 값 객체.
- `<domain>/application/port/out`: 영속성·메시징·체인 연동 포트.
- `<domain>/adapter/out`: JDBC, Kafka, Web3j 구현. 다른 계층은 구현체를 직접 참조하지 않는다.
- `config`: 의존성 조립과 기능별 활성화 설정.
- `common`: 업무 예외와 HTTP 오류 변환.

도메인은 payout, outbox, messaging, chain, reconciliation으로 나눈다.
도메인 application 계층은 다른 도메인을 호출하지 않고 도메인 간 조율은 usecase에서 수행한다.
Web DTO는 `WebRequest`/`WebResponse`, App DTO는 `AppRequest`/`AppResponse`로 끝난다.
중첩 DTO에도 같은 규칙이 적용된다.

## 트랜잭션

지급 생성은 유스케이스의 `@Transactional`로 지급·상태 이력·Outbox를 함께 커밋한다.
도메인 서비스의 `MANDATORY`는 이 경계에 참여한다.

체인·Kafka 호출 유스케이스는 `NOT_SUPPORTED`로 긴 DB 트랜잭션을 피한다.
필요한 DB 쓰기만 `TransactionTemplate`의 짧은 트랜잭션으로 묶는다.
nonce 증가와 거래 생성, 처리 완료 기록, 정산 상태와 이력, 대사 finding 반영이 각각 원자적 경계다.
단일 SQL Outbox 선점은 auto-commit 한 문장으로 완료한다.

JDBC를 사용하는 이유는 PostgreSQL의 `SKIP LOCKED`, advisory lock, JSONB,
조건부 UPDATE를 명시적으로 제어하기 위해서다. 테이블은 Flyway로만 변경한다.
V2 Todo 호환 테이블은 V3에서 제거하며 최종 스키마는 제공된 지급 테이블을 유지한다.

## 검증

ArchUnit은 controller/runtime → usecase → application/domain 및 adapter 포트 의존 방향,
순수 도메인, DTO·포트 네이밍, 트랜잭션 경계를 검사한다.
DB와 메시징 동시성은 실제 PostgreSQL/Kafka에서 별도 통합 테스트한다.
ETH 전송과 재시작 복구는 Anvil을 사용한다.
