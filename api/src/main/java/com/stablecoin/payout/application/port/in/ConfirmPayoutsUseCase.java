package com.stablecoin.payout.application.port.in;

/**
 * 노드에 보낸 지급 트랜잭션의 결과를 확인해 정산(SETTLED)하거나 실패(FAILED), 확인 필요(REQUIRES_REVIEW)로 바꾸는 입력 포트.
 */
public interface ConfirmPayoutsUseCase {

	/**
	 * @return 상태를 바꾼 지급 수
	 */
	int confirmBroadcast();

}
