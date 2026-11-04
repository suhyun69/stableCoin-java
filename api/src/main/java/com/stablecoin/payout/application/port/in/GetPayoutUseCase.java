package com.stablecoin.payout.application.port.in;

import java.util.UUID;

import com.stablecoin.payout.application.PayoutNotFoundException;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutChainTransaction;

/**
 * 지급과 그 체인 트랜잭션을 조회하는 입력 포트. DB만 읽고 노드는 부르지 않는다.
 */
public interface GetPayoutUseCase {

	/**
	 * @throws PayoutNotFoundException 없는 지급인 경우
	 */
	PayoutView get(UUID payoutId);

	/**
	 * @param chainTransaction 체인 트랜잭션. 아직 서명하지 않았으면 null
	 */
	record PayoutView(Payout payout, PayoutChainTransaction chainTransaction) {
	}

}
