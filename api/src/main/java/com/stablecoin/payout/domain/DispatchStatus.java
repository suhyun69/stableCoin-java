package com.stablecoin.payout.domain;

/**
 * 지급 전송 상태. DB payout_dispatches.status 와 같은 값이어야 한다.
 */
public enum DispatchStatus {

	/** 소비자가 이 지급의 전송을 맡음 (아직 서명 전) */
	STARTED,

	/** 서명한 트랜잭션을 저장함. 노드에 보냈는지는 아직 모름 */
	SIGNED,

	/** 노드가 트랜잭션을 받음. 블록 확정을 기다림 */
	BROADCAST,

	/** 블록에서 성공함 */
	CONFIRMED,

	/** 노드가 거부했거나 블록에서 실패함 */
	FAILED,

	/** 결과를 알 수 없어 사람이 확인해야 함 */
	REQUIRES_REVIEW

}
