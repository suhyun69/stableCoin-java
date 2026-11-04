package com.stablecoin.payout.domain;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 대사에 쓰는 장부 한 줄 (지급과 그 체인 트랜잭션).
 */
public record LedgerEntry(UUID payoutId, PayoutStatus payoutStatus, ChainTransactionStatus chainStatus,
		String transactionHash, Long includedBlockNumber, String from, String to, BigInteger amountWei) {
}
