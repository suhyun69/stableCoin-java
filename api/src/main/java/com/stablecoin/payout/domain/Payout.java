package com.stablecoin.payout.domain;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 가맹점이 요청한 지급. 금액은 최소 단위(wei) 정수다.
 */
public record Payout(UUID id, String merchantId, String recipientAddress, BigInteger amountWei,
		String idempotencyKey, PayoutStatus status) {

	public static Payout requested(String merchantId, String recipientAddress, BigInteger amountWei,
			String idempotencyKey) {
		return new Payout(UUID.randomUUID(), merchantId, recipientAddress, amountWei, idempotencyKey,
				PayoutStatus.REQUESTED);
	}

}
