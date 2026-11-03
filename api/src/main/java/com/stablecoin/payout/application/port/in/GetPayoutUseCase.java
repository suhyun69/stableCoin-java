package com.stablecoin.payout.application.port.in;

import java.util.UUID;

import com.stablecoin.payout.application.PayoutNotFoundException;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutDispatch;

/**
 * 지급과 그 전송 기록을 조회하는 입력 포트. DB만 읽고 노드는 부르지 않는다.
 */
public interface GetPayoutUseCase {

	/**
	 * @throws PayoutNotFoundException 없는 지급인 경우
	 */
	PayoutView get(UUID payoutId);

	/**
	 * @param dispatch 전송 기록. 아직 소비자가 처리하지 않았으면 null
	 */
	record PayoutView(Payout payout, PayoutDispatch dispatch) {
	}

}
