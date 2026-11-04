package com.stablecoin.payout.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 장부와 체인 전송을 비교해 불일치를 분류한다 (stableCoin TS classifyReconciliation).
 * 해시, 보낸 지갑, 받는 지갑, 금액이 모두 같아야 같은 전송으로 본다. 전송 하나는 장부 한 줄과만 짝지어진다.
 * TS와 달리 REQUIRES_REVIEW 지급은 STATUS_LAG 로 자동 정산하지 않고 STATUS_MISMATCH 로 남긴다 (사람이 확인하기로 한 지급이다).
 */
public final class ReconciliationClassifier {

	private ReconciliationClassifier() {
	}

	public static List<ReconciliationFinding> classify(List<LedgerEntry> ledger, List<ChainPayment> payments,
			long observedBlockNumber, long requiredConfirmations) {
		List<ReconciliationFinding> findings = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<ChainPayment> unique = new ArrayList<>();
		for (ChainPayment payment : payments) {
			if (seen.add(identity(payment))) {
				unique.add(payment);
			}
			else {
				findings.add(ReconciliationFinding.ofPayment(ReconciliationFinding.DUPLICATE_CHAIN_EVENT, payment));
			}
		}

		Map<LedgerEntry, ChainPayment> matched = new HashMap<>();
		Set<String> used = new HashSet<>();
		for (LedgerEntry entry : ledger) {
			ChainPayment payment = unique.stream()
				.filter(candidate -> !used.contains(identity(candidate)) && matches(entry, candidate))
				.findFirst()
				.orElse(null);
			if (payment != null) {
				used.add(identity(payment));
				matched.put(entry, payment);
			}
			else if (entry.payoutStatus() == PayoutStatus.SETTLED) {
				findings.add(ReconciliationFinding.missingChainEvent(entry));
			}
		}

		for (ChainPayment payment : unique) {
			if (!used.contains(identity(payment))) {
				findings.add(ReconciliationFinding.ofPayment(ReconciliationFinding.CHAIN_EVENT_WITHOUT_LEDGER, payment));
			}
		}

		for (LedgerEntry entry : ledger) {
			ChainPayment payment = matched.get(entry);
			if (payment != null) {
				classifyMatched(entry, payment, observedBlockNumber, requiredConfirmations, findings);
			}
		}
		return findings;
	}

	private static void classifyMatched(LedgerEntry entry, ChainPayment payment, long observedBlockNumber,
			long requiredConfirmations, List<ReconciliationFinding> findings) {
		if (entry.payoutStatus() == PayoutStatus.FAILED) {
			findings.add(ReconciliationFinding.statusMismatch(entry, "FAILED_WITH_CONFIRMED_TRANSFER"));
			return;
		}
		if (entry.includedBlockNumber() != null && entry.includedBlockNumber() != payment.blockNumber()) {
			findings.add(ReconciliationFinding.statusMismatch(entry, "STORED_BLOCK_NUMBER_MISMATCH"));
			return;
		}
		long confirmations = observedBlockNumber - payment.blockNumber() + 1;
		boolean confirmed = confirmations >= requiredConfirmations;
		if (confirmed && entry.payoutStatus() == PayoutStatus.REQUIRES_REVIEW) {
			findings.add(ReconciliationFinding.statusMismatch(entry, "REVIEW_WITH_CONFIRMED_TRANSFER"));
			return;
		}
		if (confirmed && (entry.payoutStatus() == PayoutStatus.REQUESTED
				|| entry.payoutStatus() == PayoutStatus.AWAITING_CONFIRMATION)) {
			findings.add(ReconciliationFinding.statusLag(entry, payment, confirmations));
			return;
		}
		String inconsistency = inconsistency(entry);
		if (inconsistency != null) {
			findings.add(ReconciliationFinding.statusMismatch(entry, inconsistency));
		}
	}

	/**
	 * 체인에 전송이 있는 지급의 상태가 체인 트랜잭션 상태와 어긋나면 그 이유, 맞으면 null.
	 */
	private static String inconsistency(LedgerEntry entry) {
		ChainTransactionStatus chain = entry.chainStatus();
		return switch (entry.payoutStatus()) {
			case REQUESTED -> (chain == ChainTransactionStatus.SIGNED || chain == ChainTransactionStatus.BROADCAST_UNKNOWN)
					? null : "REQUESTED_AFTER_BROADCAST";
			case AWAITING_CONFIRMATION ->
				(chain == ChainTransactionStatus.PENDING || chain == ChainTransactionStatus.MINED_SUCCESS) ? null
						: "AWAITING_WITH_UNEXPECTED_CHAIN_STATE";
			case SETTLED -> (chain == ChainTransactionStatus.CONFIRMED) ? null : "SETTLED_WITHOUT_CONFIRMED_TRANSFER";
			default -> null;
		};
	}

	private static boolean matches(LedgerEntry entry, ChainPayment payment) {
		return sameText(entry.transactionHash(), payment.transactionHash()) && sameText(entry.from(), payment.from())
				&& sameText(entry.to(), payment.to()) && entry.amountWei().equals(payment.value());
	}

	private static String identity(ChainPayment payment) {
		return payment.transactionHash().toLowerCase(Locale.ROOT) + ":" + payment.transactionIndex();
	}

	private static boolean sameText(String a, String b) {
		return a != null && a.equalsIgnoreCase(b);
	}

}
