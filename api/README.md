# StablePay API

기존 Spring Boot 프로젝트를 ETH 지급 백엔드로 포팅했다. Java 21 toolchain과 Gradle wrapper를 사용한다.
실행·호출 예제는 [루트 README](../README.md), 환경 설정은 [개발 안내](../docs/local-development.md)를 참고한다.

```sh
./gradlew bootRun
./gradlew test postgresTest integrationTest bootJar
```

| API | 성공 응답 | 역할 |
|---|---|---|
| `GET /health` | 200 | 애플리케이션 liveness |
| `GET /dependencies/chain` | 200 / 장애 시 503 | RPC 연결·chain ID |
| `POST /payouts` | 202 | 멱등 ETH 지급 요청 |
| `GET /payouts/{id}` | 200 / 없으면 404 | 지급·체인 상태 조회 |
| `POST /internal/reconciliation/runs` | 202 | Kafka 대사 요청 |

지급 생성: `Idempotency-Key` 헤더와 `merchantId`, `recipientAddress`, `amount` 문자열을 받는다.
`amount`는 ETH 단위이며 소수점 최대 18자리다. 조회의 nonce와 confirmations는 문자열이다.
대사 요청은 `fromBlock`, `toBlock` 문자열로 최대 1000개 블록을 지정한다.
202는 Kafka 수신 확인이며 대사 완료를 뜻하지 않는다. 결과는 `reconciliation_findings`에서 확인한다.

Flyway V1은 제공된 지급 스키마, V2는 기존 예제 호환, V3는 Todo 테이블 제거다.
최종 업무 테이블은 제공된 7개 테이블이다. SQL 스키마 변경은 새 마이그레이션으로만 수행한다.
