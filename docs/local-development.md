# 로컬 실행과 검증

## 인프라

```sh
cp .env.example .env
docker compose up -d --wait
docker compose ps
```

| 서비스 | 호스트 주소 | 컨테이너 내부 주소 |
|---|---|---|
| PostgreSQL | `localhost:5433` | `postgres:5432` |
| Anvil | `http://localhost:8545` | `http://anvil:8545` |
| Kafka | `localhost:9092` | `kafka:19092` |

Anvil chain ID는 31337이며 2초마다 블록을 생성한다. 테스트 지갑에 ETH가 미리 들어 있다.
Kafka는 로컬 단일 노드 KRaft 모드다. 자동 토픽 생성을 끄고 애플리케이션이 토픽을 선언한다.
일회성 `anvil-data-init` 서비스가 상태 볼륨을 Anvil 비root 사용자(UID 1000)에게 준비한다.
정상 완료 후 해당 컨테이너가 Exited로 보이는 것은 정상이다.
포트는 loopback에만 공개한다. 데이터는 서비스별 named volume에 유지한다.
Anvil은 주기적으로 상태를 저장하고 정상 종료 시 마지막 상태를 저장한다.
강제 종료 직전 상태는 유실될 수 있다.

```sh
docker compose stop
docker compose up -d --wait
```

`docker compose down`은 볼륨을 유지한다. 개발 데이터를 모두 초기화할 때만
`docker compose down -v`를 사용한다. DB만 유지하고 체인만 초기화하면 원장과 체인이 어긋난다.

## 백엔드

```sh
cd api
./gradlew bootRun
```

Compose `.env`는 백엔드 프로세스에 자동 전달되지 않는다. 사용자 정의 포트·비밀번호를 쓸 때는
아래 환경 변수를 Spring Boot 프로세스에도 지정한다.

| 환경 변수 | 기본값·역할 |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/stablepay` |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | 각각 `stablepay` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `ANVIL_RPC_URL` | `http://localhost:8545` |
| `CHAIN_ID` | `31337` |
| `ANVIL_PRIVATE_KEYS` | 쉼표로 구분한 개인키 목록. 기본값은 공개 Anvil account #0, #1 키 |
| `SENDER_ADDRESS` | V4 업그레이드 전용. 거래가 없는 기존 지급의 발신자. 기본값은 account #0 |
| `REQUIRED_CONFIRMATIONS` | `2` |
| `PAYOUT_TOPIC` | `stablepay.payout-requested.v1` |
| `PAYOUT_GROUP` | `stablepay-payout-sender` |
| `WORKERS_ENABLED` | `true`; false면 listener·스케줄러를 정지 |
| `MESSAGING_ENABLED`, `CHAIN_ENABLED` | 각각 `true`; 기능별 비활성화 |

개인키에서 주소를 파생하며, 등록된 모든 주소가 RPC `eth_accounts`에 있어야 기동한다.
RPC chain ID도 기동 시 검증한다. 요청의 `from`, `to` 모두 등록된 계정만 허용한다.
Anvil mnemonic이나 계정 수를 변경하면 `ANVIL_PRIVATE_KEYS`도 함께 변경한다.
기본 등록 계정은 #0, #1이며 다른 계정을 사용하려면 해당 개인키를 추가한다.
개인 키는 Git에 저장하지 않는다. 기본값은 누구나 아는 Anvil 로컬 테스트 키다.
폴링 간격은 `stablepay.workers.outbox-delay-ms`, `confirm-delay-ms`, `recovery-delay-ms`로 조정한다.
기본값은 각각 1000/2000/5000ms이고, 독립 작업을 위해 스케줄러 스레드 3개를 사용한다.

API와 워커를 분리할 경우 같은 실행 파일에 각각 `WORKERS_ENABLED=false/true`를 사용하고 서버 포트를 구분한다.
현재 워커 실행 파일도 HTTP 서버를 함께 띄운다.

## 테스트

```sh
cd api
./gradlew clean test postgresTest integrationTest bootJar
```

- `test`: 도메인·웹 오류·아키텍처·DLT 실패 전파 검사. 외부 서비스가 필요 없다.
- `postgresTest`: nonce·멱등성·원자적 롤백·정산·복구·대사 동시성 검사.
- `integrationTest`: 실제 Kafka 발행·재시도·DLT, Anvil 서명·전송, HTTP와 재시작 E2E.

통합 테스트는 UUID DB 스키마와 토픽을 생성하고 정리한다. 기본 개발 스키마를 삭제하지 않는다.
`TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD`로 별도 DB를 지정할 수 있다.
Kafka·Anvil 주소는 위 환경 변수를 재사용한다. 테스트는 chain ID 31337과 기본 Anvil 테스트 지갑을 기대한다.

## 장애 복구

- Outbox 발행 실패: DB에 오류·시도 횟수를 기록하고 최대 300초의 지수 지연으로 다시 시도한다.
- Publisher 중단: 30초 lease 만료 후 다른 Publisher가 같은 eventId로 이어서 발행한다.
- 소비 실패: 영구 오류는 즉시 DLT, 일시 오류는 최초 포함 5회 후 DLT. DLT 전송 확인 실패 시 원본을 완료하지 않는다.
- 서명 후 중단: Kafka 재전달과 DB에 저장된 원문을 사용한다. 신규 nonce를 할당하지 않는다.
- 응답 유실: `BROADCAST_UNKNOWN`을 기록한다. 복구 작업은 저장 hash를 조회하거나 같은 원문을 재전송한다.
- 해당 발신자의 nonce가 이미 채굴됐지만 저장 hash가 없는 경우: 자동 재지급하지 않고 검토 상태로 분리한다.
- 키 등록이 해제되어도 이미 저장된 서명 원문은 재전송한다. 대사는 과거 지급의 발신자도 포함한다.
- 정산 내용·블록 불일치: 검토 상태로 전환한다. 검토 상태는 자동 해제하지 않는다.

서명 전 RPC/가스 산정 실패가 DLT로 간 경우 CREATED 거래의 nonce는 예약된 채 남는다.
원인(예: 잔액 부족)을 해소하고 같은 이벤트를 재처리해야 한다. 예약 nonce나 거래 행을 임의 삭제하지 않는다.
로컬 구현은 수수료 인상·취소 거래를 자동 생성하지 않는다.

DLT 원인을 수정한 뒤 해당 지급 Outbox만 재발행하려면, 로컬 DB에서 대상 ID를 확인하고 실행한다.

```sql
UPDATE outbox_messages
SET status='PENDING',available_at=now(),locked_at=NULL,locked_by=NULL
WHERE id='<대상 outbox ID>' AND type='PAYOUT_REQUESTED';
```

이미 전송된 지급은 processed_messages와 거래 상태로 중복 처리된다.
대사 DLT는 원인을 수정한 뒤 같은 구간을 API로 다시 요청한다.

## 대사

```sh
curl -i -X POST http://localhost:8080/internal/reconciliation/runs \
  -H 'Content-Type: application/json' \
  -d '{"fromBlock":"0","toBlock":"100"}'

docker compose exec postgres psql -U stablepay -d stablepay \
  -c "SELECT kind,payout_id,transaction_hash,status,detail FROM reconciliation_findings ORDER BY created_at;"
```

대사 토픽은 `${PAYOUT_TOPIC}.reconciliation`, DLT는 각 토픽 뒤 `.DLT`다.
미래 블록 구간은 소비 단계에서 재시도 후 DLT로 갈 수 있으므로 채굴된 구간을 지정한다.
runId는 메시지 상관관계 식별자이며 별도 실행 이력 테이블이나 진행률 조회 API는 없다.
일치하는 체인 결과의 상태 지연은 자동 복구하고 모순은 finding과 검토 상태로 기록한다.
정산 이후의 재구성은 지정 구간 대사로 탐지한다. 자동 전체 체인 재스캔은 수행하지 않는다.
