package com.stablecoin.payout.application.port.out;

import java.util.UUID;

import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 지급 상태 변경 이력 출력 포트. 호출한 쪽의 DB 트랜잭션에 참여한다.
 */
public interface PayoutStatusHistoryPort {

	/**
	 * @param from 처음 만들 때는 null
	 */
	void record(UUID payoutId, PayoutStatus from, PayoutStatus to, String reason);

}
