package com.stablecoin.payout.application.port.in;

import java.math.BigInteger;

import com.stablecoin.payout.application.IdempotencyKeyConflictException;
import com.stablecoin.payout.domain.Payout;

/**
 * 지급 요청 입력 포트. 같은 멱등성 키로 다시 요청하면 새로 만들지 않고 처음 만든 지급을 돌려준다.
 */
public interface CreatePayoutUseCase {

	/**
	 * @throws IdempotencyKeyConflictException 같은 멱등성 키로 다른 내용을 요청한 경우
	 */
	CreatePayoutResult create(CreatePayoutCommand command);

	record CreatePayoutCommand(String merchantId, String recipientAddress, BigInteger amountWei,
			String idempotencyKey) {
	}

	/**
	 * @param replayed 같은 키의 기존 지급을 돌려준 경우 true
	 */
	record CreatePayoutResult(Payout payout, boolean replayed) {
	}

}
