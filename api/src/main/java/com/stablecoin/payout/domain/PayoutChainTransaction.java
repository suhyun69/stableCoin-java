package com.stablecoin.payout.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 지급 하나의 체인 트랜잭션. 서명한 트랜잭션(rawTransaction)과 그 해시, nonce가 들어 있다.
 * lastError 는 마지막으로 실패하거나 확인 필요로 바뀐 이유다.
 */
public record PayoutChainTransaction(UUID payoutId, ChainTransactionStatus status, String senderAddress, long nonce,
		String transactionHash, String rawTransaction, String lastError, Long includedBlockNumber, long confirmations,
		Instant broadcastAt) {

	@Override
	public String toString() {
		return "PayoutChainTransaction[payoutId=" + payoutId + ", status=" + status + ", nonce=" + nonce
				+ ", transactionHash=" + transactionHash + "]";
	}

}
