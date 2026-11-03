package com.stablecoin.payout.domain;

/**
 * 지급 상태. DB의 payouts_status_check 와 같은 값이어야 한다.
 */
public enum PayoutStatus {

	/** 지급 요청을 받아 저장함 (체인 전송 전) */
	REQUESTED,

	/** 체인으로 보냈고 블록 확정을 기다림 */
	AWAITING_CONFIRMATION,

	/** 체인에서 확정됨 */
	SETTLED,

	/** 실패함 */
	FAILED,

	/** 결과를 알 수 없어 사람이 확인해야 함 */
	REQUIRES_REVIEW

}
