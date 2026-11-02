# stableCoin-java
Outbox, Idempotence, Dual Write

## 애플리케이션 (Spring Boot)

`api/` 폴더에 있습니다.

- Java 25, Spring Boot 4.0.0, Gradle
- 패키지: `com.stablecoin`

빌드 및 테스트:

```bash
cd api && ./gradlew build
```

실행 (기본 포트 `8080`):

```bash
cd api && ./gradlew bootRun
```

노드 RPC 주소는 `CHAIN_RPC_URL` 환경 변수로 바꿀 수 있습니다 (기본값 `http://127.0.0.1:8545`).

### API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/chain/accounts` | anvil 지갑 목록과 잔액 (`balanceWei`, `balanceEth`) |
| `POST` | `/api/chain/transfers` | 지갑 간 ETH 전송 |
| `GET` | `/api/chain/transactions/{txHash}` | 트랜잭션 상세 조회 |

오류는 `application/problem+json` 형식으로 반환합니다.

- `400 Bad Request`: 요청 값이 잘못되었거나 노드가 거부함 (잔액 부족, 서명할 수 없는 지갑 등)
- `404 Not Found`: 노드에 해당 트랜잭션이 없음
- `502 Bad Gateway`: 노드에 연결할 수 없음

```bash
curl http://localhost:8080/api/chain/accounts
```

전송은 노드가 서명할 수 있는 지갑(anvil 기본 지갑)에서만 보낼 수 있습니다. 금액은 ETH 단위이며 소수점 18자리까지 입력할 수 있습니다.

```bash
curl -X POST http://localhost:8080/api/chain/transfers \
  -H 'Content-Type: application/json' \
  -d '{"from":"0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266","to":"0x70997970c51812dc3a010c7d01b50e0d17dc79c8","amountEth":1.5}'
```

응답의 `status`는 `SUCCESS`, `FAILED`, `PENDING` 중 하나입니다. 전송 후 `chain.receipt-timeout`(기본 2초) 동안 블록 포함을 기다리고, 그 안에 포함되지 않으면 `PENDING`을 반환합니다.

전송 응답의 `txHash`로 상세 정보를 조회할 수 있습니다. 상태, 블록 번호와 시각, 금액, nonce, 가스 한도/사용량, 가스 가격, 수수료(`gasUsed × gasPrice`), 입력 데이터를 반환합니다. `PENDING`이면 블록, 가스 사용량, 수수료 값은 `null`입니다.

```bash
curl http://localhost:8080/api/chain/transactions/0x...
```

API 문서 (springdoc-openapi, 앱 실행 후):

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

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
