package com.stablecoin.payout.application.port.out;

import com.stablecoin.payout.domain.Payout;

/**
 * 지급 저장소 출력 포트. save는 호출하는 즉시 저장(커밋)된다.
 */
public interface PayoutRepositoryPort {

	Payout save(Payout payout);

}
