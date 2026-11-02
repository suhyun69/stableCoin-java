package com.stablecoin.payout.application.port.in;

import java.math.BigInteger;

import com.stablecoin.payout.domain.Payout;

/**
 * 지급 요청 입력 포트.
 */
public interface CreatePayoutUseCase {

	Payout create(CreatePayoutCommand command);

	record CreatePayoutCommand(String merchantId, String recipientAddress, BigInteger amountWei,
			String idempotencyKey) {
	}

}
