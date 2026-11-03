# stableCoin-java
Outbox, Idempotence, Dual Write

## 로컬 인프라 (Docker Compose)

Postgres, 로컬 이더리움 노드(anvil), Kafka를 함께 실행합니다.

```bash
docker compose up -d
```

| 서비스 | 이미지 | 주소 | 접속 정보 |
| --- | --- | --- | --- |
| `postgres` | `postgres:17-alpine` | `localhost:5432` | DB, 사용자, 비밀번호 모두 `stablecoin` |
| `anvil` | `ghcr.io/foundry-rs/foundry:v1.8.7` | `http://127.0.0.1:8545` | chain id `31337`, 테스트 지갑 10개 |
| `kafka` | `apache/kafka:4.1.1` | 호스트: `localhost:9092`, 컨테이너끼리: `kafka:29092` | 단일 노드 KRaft (ZooKeeper 없음). 데이터는 컨테이너 안에만 있어 `down` 하면 지워짐 |

```bash
docker compose ps          # 상태 확인 (둘 다 healthy면 준비 완료)
docker compose logs -f anvil
docker compose down        # 종료 (Postgres 데이터는 볼륨에 남음)
docker compose down -v     # 종료 + Postgres 데이터 삭제
```

- 포트와 접속 정보는 환경 변수로 바꿀 수 있습니다: `POSTGRES_PORT`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `ANVIL_PORT`, `CHAIN_ID`, `KAFKA_PORT`
- Kafka 메시지 확인 (컨테이너 안의 CLI는 내부 주소 `kafka:29092`로 접속):
  ```bash
  docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
    --topic stablecoin.payout --from-beginning --property print.key=true --property print.headers=true
  ```
  ```bash
  ANVIL_PORT=8546 docker compose up -d
  ```
- anvil 상태는 메모리에만 있어 컨테이너를 재시작하면 초기화됩니다.
- `./anvil/start.sh`로 띄운 anvil과 같은 8545 포트를 쓰므로 둘 중 하나만 실행하세요. 이미 실행 중이면 `./anvil/stop.sh`로 종료하거나 `ANVIL_PORT`를 바꿉니다.

## 애플리케이션 (Spring Boot)

`api/` 폴더에 있습니다.

- Java 25, Spring Boot 4.0.0, Gradle
- 패키지: `com.stablecoin`

빌드 및 테스트:

```bash
cd api && ./gradlew build
```

실행 (기본 포트 `8080`). Postgres, anvil, Kafka가 떠 있어야 합니다 (`docker compose up -d`):

```bash
cd api && ./gradlew bootRun
```

테스트는 Testcontainers로 Postgres와 Kafka를 직접 띄우므로 Docker가 실행 중이어야 합니다. DB 스키마는 앱이 시작할 때 Flyway(`api/src/main/resources/db/migration`)가 만듭니다.

### 설정

| 환경 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `CHAIN_RPC_URL` | `http://127.0.0.1:8545` | 노드 RPC 주소 |
| `CHAIN_ID` | `31337` | 서명에 넣는 체인 ID. 앱 시작 시 노드의 체인 ID와 다르면 **앱이 시작되지 않습니다** |
| `CHAIN_DEV_WALLETS_MNEMONIC` | anvil 기본 니모닉 (`test test … junk`) | 전송에 쓸 지갑 키를 계산하는 니모닉 |
| `CHAIN_DEV_WALLETS_COUNT` | `10` | 계산할 지갑 수 |
| `CHAIN_PAYOUT_WALLET` | anvil 0번 지갑 | 지급(`POST /api/payouts`)을 보내는 회사 지갑. 위 지갑 중 하나가 아니면 **앱이 시작되지 않습니다** |
| `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5432/stablecoin` | DB 주소 (docker compose의 Postgres) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `stablecoin` / `stablecoin` | DB 계정 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka 주소 |
| `OUTBOX_PUBLISHER_ENABLED` | `true` | outbox 발행기 실행 여부 |

**개인키는 설정하지 않습니다.** anvil은 기본 니모닉에서 BIP-44 경로 `m/44'/60'/0'/0/i`로 지갑 10개를 만듭니다. 앱도 시작할 때 같은 니모닉과 경로로 키를 계산해 메모리에만 두므로, anvil을 다시 띄워도 그대로 쓸 수 있습니다. 개인키는 요청, 응답, 로그 어디에도 나가지 않고, 요청에는 보내는 지갑 주소(`from`)만 넣습니다.

기본 니모닉은 공개된 값이므로 **로컬 체인(`CHAIN_ID=31337`)에서만** 허용됩니다. 다른 체인 ID로 설정하면 앱이 시작되지 않습니다. `anvil --mnemonic`으로 니모닉을 바꿨다면 `CHAIN_DEV_WALLETS_MNEMONIC`도 같은 값으로 설정하세요.

### 코드 구조 (포트와 어댑터)

```
com.stablecoin.chain
├── domain/                  도메인 값 (Transfer, TransactionDetail, ChainReceipt, UnsignedTransaction, Eth ...)
├── application/
│   ├── port/in/             입력 포트: ChainQueryUseCase, TransferUseCase
│   ├── port/out/            출력 포트: ChainNodePort(노드), TransactionSignerPort(서명)
│   └── *Service, ChainIdGuard, 예외
└── adapter/
    ├── in/web/              REST 컨트롤러 (ChainController)
    ├── in/actuator/         헬스 체크 (ChainHealthIndicator)
    ├── in/startup/          시작 시 체인 ID 확인 (ChainIdStartupCheck)
    ├── out/rpc/             JSON-RPC 노드 어댑터 (JsonRpcChainNode, RestClient)
    └── out/signer/          서명 어댑터 (AnvilDevWalletSigner: anvil 니모닉에서 계산한 키로 서명, web3j)
```

`com.stablecoin.payout`(지급)도 같은 구조입니다. 출력 포트는 `PayoutRepositoryPort`, `PayoutStatusHistoryPort`(DB, JPA), `OutboxPort`(`com.stablecoin.outbox`), `PayoutTransferPort`(체인 전송, chain 모듈의 `TransferUseCase` 사용)입니다.

애플리케이션 계층은 포트(인터페이스)에만 의존하므로, 노드 통신 방식이나 서명 방식(예: 실제 네트워크용 KMS)을 바꿔도 어댑터만 교체하면 됩니다. 테스트에서는 포트를 가짜로 바꿔 노드와 개인키 없이 전송 절차를 검증합니다.

### API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/chain/accounts` | anvil 지갑 목록과 잔액 (`balanceWei`, `balanceEth`) |
| `GET` | `/api/chain/signers` | 전송의 `from`으로 쓸 수 있는 지갑(앱이 키를 가진 anvil 지갑) 목록과 잔액 |
| `POST` | `/api/chain/transfers` | `from` 지갑에서 ETH 전송 |
| `GET` | `/api/chain/transactions/{txHash}` | 트랜잭션 상세 조회 |
| `POST` | `/api/payouts` | 가맹점 지급 요청 (`Idempotency-Key` 헤더 필수) |
| `GET` | `/actuator/health/chain` | 노드 연결 상태 (`UP`이면 200 + `chainId`, `DOWN`이면 503) |

`/actuator/health`는 노드를 포함한 전체 상태를 보여줍니다.

오류는 `application/problem+json` 형식으로 반환하며, `code`로 오류 종류를 구분합니다.

| 상태 | `code` | 의미 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 요청 값이 잘못됨 또는 필수 헤더 없음. `detail`에 필드별 사유 |
| 400 | `UNKNOWN_SIGNER` | 앱이 `from` 지갑의 키를 갖고 있지 않음 |
| 400 | `CHAIN_REJECTED` | 노드가 요청을 거부함 (잔액 부족 등). `detail`에 노드의 사유 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 같은 `Idempotency-Key`로 다른 내용의 지급을 요청함 |
| 404 | `TRANSACTION_NOT_FOUND` | 노드에 해당 트랜잭션이 없음 |
| 503 | `CHAIN_UNAVAILABLE` | 노드에 연결할 수 없음 (연결 거부, timeout). `Retry-After` 헤더의 초만큼 기다린 뒤 다시 시도 |
| 502 | `CHAIN_ERROR` | 노드가 올바르지 않은 응답을 반환함 |
| 500 | `CHAIN_ID_MISMATCH` | 노드의 체인 ID가 설정과 다름 (앱 시작 시 노드에 연결할 수 없었던 경우) |

노드 주소 등 내부 정보는 응답에 넣지 않고 서버 로그에만 남깁니다. 노드 요청 timeout은 `spring.http.clients.connect-timeout`(1초), `spring.http.clients.read-timeout`(3초)으로 설정합니다.

```bash
curl http://localhost:8080/api/chain/accounts
```

전송은 `from`에 **보내는 지갑 주소**를 넣습니다 (`GET /api/chain/signers`의 주소, 대소문자 무관). 앱이 그 지갑의 키로 nonce, 가스 한도(`eth_estimateGas`), 수수료(`maxFeePerGas = baseFee × 2 + maxPriorityFee`)를 채운 EIP-1559 트랜잭션에 직접 서명하고 `eth_sendRawTransaction`으로 보냅니다. 금액은 ETH 단위이며 소수점 18자리까지 입력할 수 있습니다.

```bash
curl -X POST http://localhost:8080/api/chain/transfers \
  -H 'Content-Type: application/json' \
  -d '{"from":"0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266","to":"0x70997970c51812dc3a010c7d01b50e0d17dc79c8","amountEth":1.5}'
```

응답의 `nonce`는 이 전송에 쓴 nonce입니다. 같은 지갑이 같은 nonce를 두 번 쓰지 않도록 앱 인스턴스 안에서는 **지갑별로** 전송을 한 번에 하나씩 서명하고 보냅니다 (다른 지갑끼리는 동시에 처리). 이 `/transfers` 잠금은 앱 인스턴스 안에서만 걸리므로, 앱을 여러 대 띄워 같은 지갑으로 전송하면 별도의 순서 보장이 필요합니다 (지급은 아래처럼 DB 잠금을 씁니다).

응답의 `status`는 `SUCCESS`, `FAILED`, `PENDING` 중 하나입니다. 전송 후 `chain.receipt-timeout`(기본 2초) 동안 블록 포함을 기다리고, 그 안에 포함되지 않으면 `PENDING`을 반환합니다.

전송 응답의 `txHash`로 상세 정보를 조회할 수 있습니다. 상태, 블록 번호와 시각, 금액, nonce, 가스 한도/사용량, 가스 가격, 수수료(`gasUsed × gasPrice`), 입력 데이터를 반환합니다. `PENDING`이면 블록, 가스 사용량, 수수료 값은 `null`입니다.

```bash
curl http://localhost:8080/api/chain/transactions/0x...
```

### 지급 (Payout)

가맹점 지급을 요청합니다. **지급(`REQUESTED`), 첫 상태 이력, 체인 전송 요청(outbox `PAYOUT_REQUESTED`)을 한 DB 트랜잭션으로 저장**하고 `202 Accepted`를 반환합니다. 셋 다 저장되거나 셋 다 저장되지 않으므로 "기록은 있는데 보낼 일이 없음" 같은 어긋남이 생기지 않습니다.

```bash
curl -i -X POST http://localhost:8080/api/payouts \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: payout-2026-001' \
  -d '{"merchantId":"merchant_demo_01","recipientAddress":"0x70997970c51812dc3a010c7d01b50e0d17dc79c8","amountEth":25.5}'
```

**멱등성** (`Idempotency-Key` 헤더 필수)

| 같은 키로 다시 요청 | 결과 |
| --- | --- |
| 같은 내용 (가맹점, 받는 주소, 금액. 주소 대소문자 무관) | 새로 만들지 않고 **처음 지급을 그대로** `202`로 반환, 응답 헤더 `Idempotent-Replayed: true` |
| 다른 내용 | `409 IDEMPOTENCY_KEY_CONFLICT` |
| 동시에 여러 번 | DB의 UNIQUE 제약으로 하나만 저장되고, 나머지는 그 지급을 돌려받음 |

**테이블** (Flyway `V2__payout_history_and_outbox.sql`)
- `payouts`: `idempotency_key` UNIQUE, `request_fingerprint`(요청 내용의 SHA-256), 상태 `REQUESTED` / `AWAITING_CONFIRMATION` / `SETTLED` / `FAILED` / `REQUIRES_REVIEW`
- `payout_status_history`: 상태가 바뀔 때마다 한 줄 (처음: `NULL → REQUESTED`, `PAYOUT_CREATED`)
- `outbox_messages`: 나중에 처리할 일. `PENDING` 상태로 쌓이며, payload에 받는 주소와 금액(wei)을 담음

### outbox 발행기 (Kafka)

앱 안의 발행기가 1초마다 `PENDING` outbox 메시지를 잡아 Kafka로 보내고, Kafka가 받았다고 확인(`acks=all`)하면 `PUBLISHED`로 바꿉니다.

```
POST /api/payouts ─(한 트랜잭션)→ payouts + history + outbox(PENDING)
발행기 ─ claim (FOR UPDATE SKIP LOCKED, 30초 임대) → Kafka 발행 → outbox PUBLISHED
```

| 항목 | 값 |
| --- | --- |
| 토픽 | `stablecoin.payout` (파티션 3) |
| 키 | 지급 ID. 같은 지급의 이벤트는 같은 파티션으로 가서 순서가 지켜짐 |
| 헤더 | `outbox-message-id`, `event-type` (`PAYOUT_REQUESTED`) |
| 값 | `{"messageId", "type", "aggregateType", "aggregateId", "payload": {"payoutId", "recipientAddress", "amountWei"}}` |

`outbox_messages`에서 바뀌는 값:

| 단계 | `status` | `attempts` | `locked_at` / `locked_by` | 그 밖 |
| --- | --- | --- | --- | --- |
| 지급 요청 | `PENDING` | 0 | NULL | |
| 발행기가 잡음 | `PENDING` | +1 | 잡은 시각 / 워커 ID | |
| 발행 성공 | **`PUBLISHED`** | 그대로 | NULL | `published_at` 기록, `last_error` 지움 |
| 발행 실패 | `PENDING` | 그대로 | NULL | `last_error` 기록, `available_at`을 1s → 2s → 4s … (최대 5분) 뒤로 |
| `max-attempts`(10)번 실패 | **`FAILED`** | | NULL | 더 이상 시도하지 않음 |

- **여러 워커:** `SKIP LOCKED`로 같은 메시지를 두 워커가 잡지 않습니다. 잡은 워커가 죽으면 30초(`outbox.publisher.lease`) 뒤 다른 워커가 다시 잡습니다.
- **중복 가능 (at-least-once):** Kafka 발행 후 `PUBLISHED`로 바꾸기 전에 죽으면 같은 메시지가 다시 발행됩니다. 받는 쪽은 `outbox-message-id`로 중복을 걸러야 합니다.
- 한 메시지가 실패해도 같은 묶음의 나머지는 계속 발행합니다.

### 지급 소비자와 정산

`stablecoin.payout`의 `PAYOUT_REQUESTED`를 받은 소비자가 지급 지갑(`CHAIN_PAYOUT_WALLET`)에서 실제로 보내고, 확정 확인 작업이 영수증을 보고 정산합니다.

```
PAYOUT_REQUESTED (Kafka)
  → 소비자: 전송 기록 STARTED (지급마다 한 번만)
           → 서명 → 서명한 트랜잭션을 DB에 먼저 저장 (SIGNED)
           → 노드에 보냄 → 지급 AWAITING_CONFIRMATION, 전송 기록 BROADCAST
  → 확정 확인 (2초마다): 영수증 성공 → 정산: 지급 SETTLED + 가맹점 알림 MERCHANT_PAYOUT_SETTLED (outbox → Kafka)
```

| 상황 | 지급 상태 | 전송 기록 (`payout_dispatches`) | 가맹점 알림 |
| --- | --- | --- | --- |
| 노드가 받음 | `AWAITING_CONFIRMATION` | `BROADCAST` | |
| 블록에서 성공 | **`SETTLED`** | `CONFIRMED` | `MERCHANT_PAYOUT_SETTLED` |
| 블록에서 실패 (revert) | `FAILED` | `FAILED` | `MERCHANT_PAYOUT_FAILED` |
| 서명할 때 노드가 거부 (잔액 부족 등, 아무것도 보내지 않음) | `FAILED` | `FAILED` | `MERCHANT_PAYOUT_FAILED` |
| 보낼 때 노드가 거부 / 10분(`payout.confirmation.review-after`) 동안 영수증 없음 | `REQUIRES_REVIEW` | `REQUIRES_REVIEW` | |

**두 번 보내지 않고, 멈추지도 않게**
- **메시지마다 한 번만 (inbox):** 받은 메시지를 `processed_messages`(`consumer_name`, `message_id`)에 기록하고, 같은 트랜잭션에서 전송 기록(`STARTED`)을 만듭니다. 같은 메시지를 다시 받으면 건너뜁니다.
- **지급마다 한 번만:** `payout_dispatches.payout_id`가 UNIQUE라 메시지 ID가 달라도 전송은 한 번만 진행합니다.
- **보내기 전에 저장:** 서명한 트랜잭션(raw, hash, nonce)을 DB에 커밋한 뒤 노드에 보냅니다. 보내다 죽어도 같은 트랜잭션을 다시 보내면 되고, 같은 nonce라 두 번 나가지 않습니다. 노드가 이미 아는 트랜잭션이면 성공으로 봅니다.
- **인스턴스 간 nonce 잠금:** 서명하는 트랜잭션 안에서 지급 지갑 주소로 Postgres advisory lock(`pg_advisory_xact_lock`)을 겁니다. nonce는 `max(노드 pending nonce, SIGNED 최대 nonce + 1)`이고, 잠금은 SIGNED 저장을 커밋할 때 풀리므로 앱을 여러 대 띄워도 다음 인스턴스가 방금 쓴 nonce를 봅니다. 번호를 미리 올려 두는 카운터가 아니라서 서명이 실패해도 nonce가 비지 않습니다.
- **멈춘 전송 이어가기:** `STARTED`/`SIGNED`에 30초(`payout.dispatcher.stuck-after`) 넘게 머문 전송은 확정 확인 작업이 이어서 진행합니다 (예: 노드가 잠깐 꺼졌던 경우).
- **정산은 조건부:** 지급이 기대한 상태일 때만 바꾸고, 이미 정산됐으면 그대로 둡니다. 가맹점 ID·금액·주소는 DB에서 읽습니다.
- 상태가 바뀔 때마다 `payout_status_history`에 남습니다 (`REQUESTED → AWAITING_CONFIRMATION → SETTLED`).

**소비자 오류 처리** (예외 종류로 분류)

| 분류 | 예외 | 처리 | DLT `failure-kind` |
| --- | --- | --- | --- |
| 재시도 | 노드·DB 연결 불가, deadlock, 직렬화 실패 등 일시 장애 | 1s, 2s, 4s … (최대 30s 간격, 총 5분) 다시 처리, 그래도 안 되면 DLT | `RETRIES_EXHAUSTED` (원인 해결 후 그대로 다시 넣어도 됨) |
| 영구 실패 | 해석할 수 없는 메시지 (JSON 깨짐, `payoutId` 없음) | 바로 DLT | `PERMANENT` (메시지를 고쳐야 함) |
| 확인 필요 | 그 밖의 예상하지 못한 예외 | 바로 DLT | `REQUIRES_REVIEW` (사람이 확인) |

DLT는 `stablecoin.payout.DLT`이며, 원래 메시지에 `failure-kind` 헤더와 예외 정보 헤더(`kafka_dlt-exception-cause-fqcn`, `kafka_dlt-exception-message` 등)가 붙습니다. 깨진 메시지가 같은 파티션의 다른 지급을 막지 않습니다. 재시도 간격은 `payout.dispatcher.retry.initial-interval` / `max-interval` / `max-elapsed`로 바꿀 수 있습니다.

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
  --topic stablecoin.payout.DLT --from-beginning --property print.headers=true
```

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `PAYOUT_DISPATCHER_ENABLED` | `true` | 소비자와 확정 확인 작업 실행 여부 |
| `payout.confirmation.interval` | `2s` | 확정 확인 주기 |
| `payout.confirmation.review-after` | `10m` | 영수증 없이 이 시간이 지나면 `REQUIRES_REVIEW` |
| `payout.dispatcher.stuck-after` | `30s` | 멈춘 전송으로 보는 시간 |

API 문서 (springdoc-openapi, 앱 실행 후):

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

`Chain` 태그에 체인 API가, `Payout` 태그에 지급 API가, `Actuator` 태그에 헬스 체크(`/actuator/health`, `/actuator/health/chain`)가 있으며 Swagger UI에서 바로 호출해 볼 수 있습니다.

## 로컬 노드 (Anvil)

개발용 로컬 이더리움 노드는 [Foundry](https://github.com/foundry-rs/foundry)의 `anvil`로 실행합니다.

```
anvil/
├── install.sh   # Foundry 설치/업데이트
├── start.sh     # anvil 실행
└── stop.sh      # anvil 종료
```

### 빠른 시작

```bash
./anvil/install.sh
```

```bash
./anvil/start.sh
```

노드는 포그라운드로 실행되고 `127.0.0.1:8545`(chain id `31337`)에서 RPC 요청을 받습니다. 종료는 `Ctrl+C` 또는 다른 터미널에서 `./anvil/stop.sh`.

### 스크립트

| 스크립트 | 설명 |
| --- | --- |
| `./anvil/install.sh` | Foundry가 없으면 설치하고 `foundryup`으로 업데이트합니다. anvil이 실행 중이면 업데이트를 건너뜁니다. |
| `./anvil/start.sh` | `anvil --chain-id 31337`을 실행합니다. anvil이 없으면 `install.sh` 실행을 안내합니다. |
| `./anvil/stop.sh` | `ANVIL_PORT` 포트의 anvil을 종료합니다. |
| `./anvil/stop.sh --all` | 실행 중인 모든 anvil을 종료합니다. |

### 환경 변수

| 변수 | 기본값 | 사용 스크립트 |
| --- | --- | --- |
| `CHAIN_ID` | `31337` | `start.sh` |
| `ANVIL_HOST` | `127.0.0.1` | `start.sh` |
| `ANVIL_PORT` | `8545` | `start.sh`, `stop.sh` |

```bash
ANVIL_PORT=8546 ./anvil/start.sh
```

```bash
ANVIL_PORT=8546 ./anvil/stop.sh
```

### 동작 확인

```bash
cast chain-id --rpc-url http://127.0.0.1:8545
```

`31337`이 출력되면 정상입니다. anvil은 시작할 때 10,000 ETH가 들어 있는 테스트 계정 10개와 개인키를 출력합니다. 이 개인키는 공개된 기본값이므로 로컬에서만 사용하세요.

### Foundry 업데이트

`foundryup`은 anvil이 실행 중이면 업데이트하지 않으므로 노드를 먼저 종료합니다.

```bash
./anvil/stop.sh --all && ./anvil/install.sh
```

### 참고

- 스크립트는 `~/.foundry/bin`을 PATH에 직접 추가하므로 따로 설정하지 않아도 됩니다. 터미널에서 `anvil`, `cast`, `forge`를 바로 쓰려면 PATH에 추가하세요.

  ```bash
  echo 'export PATH="$PATH:$HOME/.foundry/bin"' >> ~/.zshrc && source ~/.zshrc
  ```

- anvil 상태는 메모리에만 저장되므로 노드를 종료하면 블록과 배포한 컨트랙트가 모두 초기화됩니다.
