# StablePay Java

Java 21 / Spring Boot / PostgreSQL / Kafka / Anvil 기반 로컬 ETH 지급 백엔드.

## 실행

```sh
cp .env.example .env
docker compose up -d --wait
cd api
./gradlew bootRun
```

기본 API 주소는 `http://localhost:8080`, Swagger UI는 `http://localhost:8080/swagger-ui.html`이다.
기본 지갑은 공개된 Anvil 테스트 지갑이며 chain ID는 31337이다.
Compose `.env`는 백엔드 프로세스에 자동 전달되지 않는다. 설정 변경은 [개발 안내](docs/local-development.md)를 참고한다.

```sh
curl -i -X POST http://localhost:8080/payouts \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: example-eth-1' \
  -d '{"merchantId":"demo","recipientAddress":"0x70997970c51812dc3a010c7d01b50e0d17dc79c8","amount":"0.001"}'

curl http://localhost:8080/payouts/<응답의-id>
```

## 처리 흐름

API → 지급·이력·Outbox 원자적 저장 → Kafka → 지급 Consumer → ETH 전송.
별도 스케줄러가 영수증 확인과 불명확한 전송 복구를 수행한다.
대사 API는 Kafka로 블록 구간 작업을 전달하고 체인·원장 불일치를 기록한다.
동일 멱등 키와 같은 요청은 같은 지급을 반환하며, 다른 내용은 409이다.

## 검증

```sh
cd api
./gradlew test
./gradlew postgresTest
./gradlew integrationTest
./gradlew bootJar
```

`postgresTest`에는 PostgreSQL이, `integrationTest`에는 PostgreSQL·Kafka·Anvil이 필요하다.
통합 테스트는 고유 DB 스키마·Kafka 토픽을 만들고 정리하며 Anvil에서는 테스트 ETH를 전송한다.
E2E는 실제 HTTP·Kafka·ETH 정산, 앱 재시작, 저장된 서명 재사용, 중복 이벤트와 대사 복구를 검증한다.

## 문서·범위

- [실행·환경 설정·장애 복구](docs/local-development.md)
- [지급·멱등성·Kafka·대사 규칙](docs/payout-design.md)
- [백엔드 아키텍처](api/architecture.md)

관리 지갑의 직접 ETH 지급이 범위다. 토큰 전송·컨트랙트 내부 ETH 이동(trace)·입금 원장은 포함하지 않는다.
로컬 개발용이며 API 인증·운영 키 보관·가스 교체 거래 정책은 별도 운영 확장 사항이다.
`Source Code/`는 로컬 참고 예제로 Git에서 제외한다. 저장소는 루트 하나만 사용한다.
