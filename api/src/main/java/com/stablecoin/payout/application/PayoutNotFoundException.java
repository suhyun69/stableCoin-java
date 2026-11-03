package com.stablecoin.payout.application;

import java.util.UUID;

/**
 * 없는 지급을 조회했을 때 발생한다.
 */
public class PayoutNotFoundException extends RuntimeException {

	public PayoutNotFoundException(UUID payoutId) {
		super("지급을 찾을 수 없습니다: " + payoutId);
	}

}
