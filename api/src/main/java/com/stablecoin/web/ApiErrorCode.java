package com.stablecoin.web;

/**
 * 오류 응답(ProblemDetail)의 code 값.
 */
public enum ApiErrorCode {

	/** 요청 값 검증 실패 */
	INVALID_REQUEST,

	/** 노드가 요청을 거부함 (잔액 부족, 서명할 수 없는 지갑 등) */
	CHAIN_REJECTED,

	/** 트랜잭션이 없음 */
	TRANSACTION_NOT_FOUND,

	/** 노드에 연결할 수 없음. 잠시 후 다시 시도할 수 있음 */
	CHAIN_UNAVAILABLE,

	/** 노드가 올바르지 않은 응답을 반환함 */
	CHAIN_ERROR,

	/** 앱이 from 지갑의 키를 갖고 있지 않음 */
	UNKNOWN_SIGNER,

	/** 노드의 체인 ID가 설정과 다름 */
	CHAIN_ID_MISMATCH,

	/** 같은 Idempotency-Key로 다른 내용을 요청함 */
	IDEMPOTENCY_KEY_CONFLICT,

	/** 지급이 없음 */
	PAYOUT_NOT_FOUND

}
