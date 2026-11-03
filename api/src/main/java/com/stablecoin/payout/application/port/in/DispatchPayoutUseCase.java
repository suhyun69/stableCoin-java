package com.stablecoin.payout.application.port.in;

import java.util.UUID;

/**
 * 지급 전송 입력 포트. 같은 지급으로 여러 번 호출해도 체인 전송은 한 번만 일어난다.
 */
public interface DispatchPayoutUseCase {

	/**
	 * PAYOUT_REQUESTED 메시지를 받은 소비자가 호출한다. 메시지를 inbox(processed_messages)에 기록하고,
	 * 이미 처리한 메시지면 아무것도 하지 않는다.
	 */
	void dispatch(UUID payoutId, String messageId);

	/**
	 * 메시지 기록 없이 이 지급의 전송을 이어서 진행한다 (재개 작업, 수동 처리).
	 */
	void dispatch(UUID payoutId);

	/**
	 * 중간에 멈춘 전송(STARTED, SIGNED 에 오래 머문 것)을 이어서 진행한다.
	 * @return 다시 진행한 수
	 */
	int resumeStuck();

}
