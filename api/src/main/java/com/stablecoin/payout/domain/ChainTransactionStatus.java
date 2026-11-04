package com.stablecoin.payout.domain;

/**
 * 지급 트랜잭션의 체인 상태. DB chain_transactions.status 와 같은 값이어야 한다.
 */
public enum ChainTransactionStatus {

	/** 의도만 기록함. 이 앱은 서명과 함께 저장하므로 쓰지 않는다 */
	CREATED,

	/** 서명한 트랜잭션을 저장함. 노드에 보내기 전 */
	SIGNED,

	/** 보냈지만 노드가 받았는지 모름 (연결 불가, timeout). 같은 트랜잭션을 다시 보낸다 */
	BROADCAST_UNKNOWN,

	/** 노드가 트랜잭션을 받음. 블록 포함을 기다림 */
	PENDING,

	/** 블록에서 성공함. 확정 블록 수를 기다림 */
	MINED_SUCCESS,

	/** 블록에서 실패함 (revert) */
	MINED_REVERTED,

	/** 확정 블록 수를 채움 */
	CONFIRMED,

	/** 이 트랜잭션은 블록에 들어가지 못함 (노드가 거부했거나 nonce를 다른 트랜잭션이 씀) */
	FAILED

}
