package com.stablecoin.payout.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 지급 하나의 전송 기록. SIGNED 이후에는 서명한 트랜잭션(signedTx)과 그 해시, nonce가 들어 있다.
 */
public record PayoutDispatch(UUID payoutId, DispatchStatus status, String fromAddress, Long nonce, String txHash,
		String signedTx, Instant updatedAt) {

	@Override
	public String toString() {
		return "PayoutDispatch[payoutId=" + payoutId + ", status=" + status + ", nonce=" + nonce + ", txHash=" + txHash
				+ "]";
	}

}
