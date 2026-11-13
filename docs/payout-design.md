# ETH 지급 규칙

- API 금액은 ETH 십진 문자열, 저장 값은 wei 정수이다. 소수점은 최대 18자리이며 반올림하지 않는다.
- 금액은 양수 uint256 범위이다. 가스 비용은 지급 금액에 포함하지 않는다.
- 주소는 `0x` + 40자리 16진수로 검증하고 소문자로 정규화한다. 영주소 지급은 거부한다.
  현재 형식 검증은 EIP-55 혼합 대소문자 체크섬을 검증하지 않는다.
- fingerprint는 가맹점 ID, 정규화된 발신·수신 주소, wei로 계산한다. `1`, `1.0`은 같은 요청이다.
  이는 원본 문자열을 그대로 해싱하는 TypeScript 예제와 의도적으로 다른 동작이다.
- 멱등 키는 제공 DDL대로 전체 지급에서 유일하다. 가맹점별 복합 키가 아니다.
- 완료된 지급은 다시 전송 상태로 돌아가지 않는다. 대사 불일치 시 검토 상태로만 이동한다.
- `REQUIRES_REVIEW`는 자동 정산하지 않는다. 별도 명시적 해결 정책이 필요하다.

## 현재 API

`GET /wallets`는 결제에 사용할 수 있는 등록된 Anvil 계정과 ETH 잔액을 반환한다.
응답은 `chainId`, `blockNumber`, `wallets`이며, 각 지갑은 `address`, `balanceEth`,
`balanceWei`를 포함한다. 잔액과 블록 번호는 정밀도 보존을 위해 문자열로 반환한다.
모든 잔액은 동일한 최신 블록 번호(`latest`, 최종성 보장 아님)를 기준으로 조회하며,
잔액이 0인 지갑도 포함한다. 개인키는 반환하지 않는다. RPC 조회 실패 시 503이다.
등록하지 않은 Anvil 계정은 이 목록에 포함하지 않는다. 체인 기능을 끄면 엔드포인트도 비활성화된다.

`POST /payouts`는 `Idempotency-Key` 헤더와 `merchantId`, `from`, `to`, `amount`를 받는다.
`from`, `to` 모두 서버에 개인키가 등록된 Anvil 계정이어야 하며, 생성 응답에도 포함한다.
성공 시 202와 `Location`을 반환하며 같은 키·같은 내용은 기존 지급을 반환한다.
같은 키의 다른 내용은 409, 입력 오류는 400이다.

`GET /payouts/{id}`는 지급 상태와 `chain`을 반환한다. 거래 생성 전 `chain`은 null이다.
nonce와 confirmations는 정수 정밀도 보존을 위해 JSON 문자열이다. 없는 지급은 404이다.

Kafka Publisher/Consumer, 실제 ETH 전송, 정산 확인, 장애 복구와 블록 구간 대사까지 구현되어 있다.

## Kafka 실패 처리

소비는 최초 1회 + 지수 지연 재시도 4회 후 같은 파티션의 `<topic>.DLT`에 원본을 보관한다.
잘못된 이벤트·지원하지 않는 버전·존재하지 않는 지급·업무 충돌은 즉시 DLT로 보낸다.
DLT 발행 확인이 실패하면 원래 메시지 처리를 완료하지 않는다. DLT 이동 자체로 지급을 FAILED로 바꾸지 않는다.
전송 결과가 불명확한 경우는 메시지 재시도 대신 DB의 BROADCAST_UNKNOWN 복구 대상이다.

DLT 원인을 수정한 후 해당 지급 Outbox 행만 `status='PENDING',available_at=now(),locked_at=NULL,locked_by=NULL`
로 되돌리면 Publisher가 같은 eventId를 재발행한다. processed_messages나 nonce를 삭제하지 않는다.
이미 전송된 지급의 재처리는 기존 거래를 유지하며, 검토 상태는 수동 조사 전 자동 해제하지 않는다.


## ETH 대사와 정산

등록된 지갑과 과거 지급 발신 지갑의 직접 ETH 전송을 거래 해시로 비교한다. ERC-20 로그를 사용하지 않으며
`log_index`는 NULL이다. 동일 발신자/nonce의 관측과 저장된 서명 원문으로 중복 지급을 방지한다.
영수증 성공, 거래의 from/to/value/nonce, 포함 블록과 canonical block hash를 대조한다.
확인 수는 `현재 블록 - 포함 블록 + 1`이며 기본 임계값은 2다.

상태 지연은 정산 상태로 반영하고 resolved finding을 남긴다. 거래 누락·내용 불일치·최종 상태 모순은
open finding과 REQUIRES_REVIEW로 분리한다. 대사 DB 반영은 전역 advisory lock으로 직렬화한다.
따라서 NULL log_index가 기존 부분 고유 인덱스에 포함되지 않아도 동일 ETH finding을 중복 생성하지 않는다.
범위 내 재검증에서 조건이 해소된 finding은 resolved_at과 함께 닫되 지급의 검토 상태는 자동 해제하지 않는다.

## 예제와 달라진 점

- TypeScript/Fastify/viem 대신 Java/Spring Boot/Web3j를 사용한다.
- USDC 6자리·Transfer 로그 대신 ETH 18자리·직접 거래 내용을 검증한다.
- BullMQ/Redis 대신 Kafka 지급·대사 토픽과 DLT를 사용하며 PostgreSQL Outbox를 유지한다.
- 제공 스키마에 없는 payout_dispatches를 만들지 않고 processed_messages와 chain_transactions를 사용한다.
- nonce 할당과 거래 생성을 같은 DB 트랜잭션으로 묶고 불명확한 전송의 원문 재전송을 연결했다.
- fingerprint는 정규화한 주소·wei를 사용한다. 응답의 nonce·confirmations는 문자열이다.
- 입금 원장 스키마가 제공되지 않아 별도 입금 수집과 컨트랙트 내부 전송 추적은 범위에서 제외한다.

## 다중 발신자 서명과 복구

지급 생성 시 발신 주소를 원장에 저장한다. Kafka 키는 `chainId:senderAddress`이며
nonce는 발신 주소별로 DB에서 할당한다. 해당 개인키로 서명한 원문과 해시를 먼저 저장한 뒤
`eth_sendRawTransaction`으로 전송한다. 복구는 저장된 원문을 재사용하고, 저장된 발신자의
confirmed nonce로 충돌을 판단한다. 키 등록 해제로 서명 원문 재전송을 막지 않는다.
대사 시 지급 원장과 체인 거래의 발신·수신 주소와 금액이 모두 일치해야 한다.

신규 fingerprint는 v2이며 발신 주소를 포함한다. 기존 v1 fingerprint는 저장된 발신 주소가
요청과 일치할 때만 호환한다. V4는 체인 거래가 있는 지급의 발신자를 복사한다.
거래가 없는 지급에는 기존 `SENDER_ADDRESS`를 사용한다. 사용자 정의 발신자를 썼다면
첫 업그레이드 시 같은 환경 변수를 설정해야 한다. 이후 신규 지급은 이 변수를 사용하지 않는다.
