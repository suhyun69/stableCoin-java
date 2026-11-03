package com.stablecoin.payout.application.port.in;

import java.util.UUID;

/**
 * 지급 전송 입력 포트. PAYOUT_REQUESTED 이벤트를 받은 소비자가 호출한다.
 * 같은 지급으로 여러 번 호출해도 체인 전송은 한 번만 일어난다.
 */
public interface DispatchPayoutUseCase {

	void dispatch(UUID payoutId);

	/**
	 * 중간에 멈춘 전송(STARTED, SIGNED 에 오래 머문 것)을 이어서 진행한다.
	 * @return 다시 진행한 수
	 */
	int resumeStuck();

}
