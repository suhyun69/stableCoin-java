# ETH 지급 규칙

- API 금액은 ETH 십진 문자열, 저장 값은 wei 정수이다. 소수점은 최대 18자리이며 반올림하지 않는다.
- 금액은 양수 uint256 범위이다. 가스 비용은 지급 금액에 포함하지 않는다.
- 주소는 `0x` + 40자리 16진수로 검증하고 소문자로 정규화한다. 영주소 지급은 거부한다.
  현재 형식 검증은 EIP-55 혼합 대소문자 체크섬을 검증하지 않는다.
- fingerprint는 가맹점 ID, 정규화된 주소, wei로 계산한다. `1`, `1.0`은 같은 요청이다.
  이는 원본 문자열을 그대로 해싱하는 TypeScript 예제와 의도적으로 다른 동작이다.
- 멱등 키는 제공 DDL대로 전체 지급에서 유일하다. 가맹점별 복합 키가 아니다.
- 완료된 지급은 다시 전송 상태로 돌아가지 않는다. 대사 불일치 시 검토 상태로만 이동한다.
- `REQUIRES_REVIEW`는 자동 정산하지 않는다. 별도 명시적 해결 정책이 필요하다.

## 현재 API

`POST /payouts`는 `Idempotency-Key` 헤더와 `merchantId`, `recipientAddress`, `amount`를 받는다.
성공 시 202와 `Location`을 반환하며 같은 키·같은 내용은 기존 지급을 반환한다.
같은 키의 다른 내용은 409, 입력 오류는 400이다.

`GET /payouts/{id}`는 지급 상태와 `chain`을 반환한다. 거래 생성 전 `chain`은 null이다.
nonce와 confirmations는 정수 정밀도 보존을 위해 JSON 문자열이다. 없는 지급은 404이다.

지급 생성과 Outbox 저장까지 구현되었으며 Kafka Publisher/Consumer 및 실제 ETH 전송은 후속 단계다.

## Kafka 실패 처리

소비는 최초 1회 + 지수 지연 재시도 4회 후 같은 파티션의 `<topic>.DLT`에 원본을 보관한다.
잘못된 이벤트·지원하지 않는 버전·존재하지 않는 지급·업무 충돌은 즉시 DLT로 보낸다.
DLT 발행 확인이 실패하면 원래 메시지 처리를 완료하지 않는다. DLT 이동 자체로 지급을 FAILED로 바꾸지 않는다.
전송 결과가 불명확한 경우는 메시지 재시도 대신 DB의 BROADCAST_UNKNOWN 복구 대상이다.

DLT 원인을 수정한 후 해당 지급 Outbox 행만 `status='PENDING',available_at=now(),locked_at=NULL,locked_by=NULL`
로 되돌리면 Publisher가 같은 eventId를 재발행한다. processed_messages나 nonce를 삭제하지 않는다.
이미 전송된 지급의 재처리는 기존 거래를 유지하며, 검토 상태는 수동 조사 전 자동 해제하지 않는다.
