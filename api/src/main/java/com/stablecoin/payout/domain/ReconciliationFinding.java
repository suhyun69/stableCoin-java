package com.stablecoin.payout.domain;

import java.util.UUID;

/**
 * 장부와 체인의 불일치 한 건 (reconciliation_findings). 지급과 이어지지 않는 전송이면 payoutId 가 null 이다.
 * logIndex 는 ETH 전송에 로그가 없어 블록 안의 트랜잭션 위치를 담는다.
 */
public record ReconciliationFinding(String kind, UUID payoutId, String transactionHash, Integer logIndex,
		Long blockNumber, Long confirmations, String reason) {

	/** 체인은 확정됐는데 장부가 늦음. 자동으로 정산한다 */
	public static final String STATUS_LAG = "STATUS_LAG";

	/** SETTLED 인데 체인에 전송이 없음 */
	public static final String MISSING_CHAIN_EVENT = "MISSING_CHAIN_EVENT";

	/** 체인에 전송이 있는데 장부에 없음 */
	public static final String CHAIN_EVENT_WITHOUT_LEDGER = "CHAIN_EVENT_WITHOUT_LEDGER";

	/** 같은 전송을 두 번 읽음 */
	public static final String DUPLICATE_CHAIN_EVENT = "DUPLICATE_CHAIN_EVENT";

	/** 장부 상태와 체인 사실이 어긋남 */
	public static final String STATUS_MISMATCH = "STATUS_MISMATCH";

	static ReconciliationFinding statusLag(LedgerEntry entry, ChainPayment payment, long confirmations) {
		return new ReconciliationFinding(STATUS_LAG, entry.payoutId(), entry.transactionHash(),
				payment.transactionIndex(), payment.blockNumber(), confirmations, null);
	}

	static ReconciliationFinding missingChainEvent(LedgerEntry entry) {
		return new ReconciliationFinding(MISSING_CHAIN_EVENT, entry.payoutId(), entry.transactionHash(), null, null, null,
				null);
	}

	static ReconciliationFinding ofPayment(String kind, ChainPayment payment) {
		return new ReconciliationFinding(kind, null, payment.transactionHash(), payment.transactionIndex(),
				payment.blockNumber(), null, null);
	}

	static ReconciliationFinding statusMismatch(LedgerEntry entry, String reason) {
		return new ReconciliationFinding(STATUS_MISMATCH, entry.payoutId(), entry.transactionHash(), null, null, null,
				reason);
	}

	/**
	 * 지급이 REQUIRES_REVIEW 로 바뀐 이유를 사람이 볼 불일치로 남긴다.
	 */
	public static ReconciliationFinding review(String kind, UUID payoutId, String transactionHash, String reason) {
		return new ReconciliationFinding(kind, payoutId, transactionHash, null, null, null, reason);
	}

}
