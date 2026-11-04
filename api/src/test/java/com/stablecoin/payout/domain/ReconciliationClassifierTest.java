package com.stablecoin.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReconciliationClassifierTest {

	private static final String WALLET = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger AMOUNT = BigInteger.TEN;

	private static final long HEAD = 12;

	private static final long REQUIRED = 2;

	@Test
	void settledPayoutWithItsTransferIsConsistent() {
		assertThat(classify(entry(PayoutStatus.SETTLED, ChainTransactionStatus.CONFIRMED), payment(10))).isEmpty();
	}

	@Test
	void confirmedTransferOfAWaitingPayoutIsStatusLag() {
		assertThat(classify(entry(PayoutStatus.AWAITING_CONFIRMATION, ChainTransactionStatus.PENDING), payment(10)))
			.singleElement()
			.satisfies(finding -> {
				assertThat(finding.kind()).isEqualTo(ReconciliationFinding.STATUS_LAG);
				assertThat(finding.blockNumber()).isEqualTo(10);
				assertThat(finding.confirmations()).isEqualTo(3);
			});
	}

	@Test
	void confirmedTransferOfAPayoutThatNeverLearnedItWasSentIsStatusLag() {
		assertThat(classify(entry(PayoutStatus.REQUESTED, ChainTransactionStatus.BROADCAST_UNKNOWN), payment(10)))
			.singleElement()
			.extracting(ReconciliationFinding::kind)
			.isEqualTo(ReconciliationFinding.STATUS_LAG);
	}

	@Test
	void transferThatIsNotDeepEnoughYetIsNotAFinding() {
		assertThat(classify(entry(PayoutStatus.AWAITING_CONFIRMATION, ChainTransactionStatus.MINED_SUCCESS), payment(12)))
			.isEmpty();
	}

	@Test
	void payoutUnderReviewIsReportedInsteadOfSettled() {
		assertThat(classify(entry(PayoutStatus.REQUIRES_REVIEW, ChainTransactionStatus.PENDING), payment(10)))
			.singleElement()
			.satisfies(finding -> {
				assertThat(finding.kind()).isEqualTo(ReconciliationFinding.STATUS_MISMATCH);
				assertThat(finding.reason()).isEqualTo("REVIEW_WITH_CONFIRMED_TRANSFER");
			});
	}

	@Test
	void failedPayoutWithATransferIsAMismatch() {
		assertThat(classify(entry(PayoutStatus.FAILED, ChainTransactionStatus.MINED_REVERTED), payment(10)))
			.singleElement()
			.extracting(ReconciliationFinding::reason)
			.isEqualTo("FAILED_WITH_CONFIRMED_TRANSFER");
	}

	@Test
	void settledPayoutWithoutATransferIsMissing() {
		LedgerEntry entry = entry(PayoutStatus.SETTLED, ChainTransactionStatus.CONFIRMED);

		assertThat(ReconciliationClassifier.classify(List.of(entry), List.of(), HEAD, REQUIRED)).singleElement()
			.extracting(ReconciliationFinding::kind)
			.isEqualTo(ReconciliationFinding.MISSING_CHAIN_EVENT);
	}

	@Test
	void waitingPayoutWithoutATransferIsNotAFinding() {
		LedgerEntry entry = entry(PayoutStatus.AWAITING_CONFIRMATION, ChainTransactionStatus.PENDING);

		assertThat(ReconciliationClassifier.classify(List.of(entry), List.of(), HEAD, REQUIRED)).isEmpty();
	}

	@Test
	void transferNotInTheLedgerIsReported() {
		assertThat(ReconciliationClassifier.classify(List.of(), List.of(payment(10)), HEAD, REQUIRED)).singleElement()
			.satisfies(finding -> {
				assertThat(finding.kind()).isEqualTo(ReconciliationFinding.CHAIN_EVENT_WITHOUT_LEDGER);
				assertThat(finding.payoutId()).isNull();
				assertThat(finding.logIndex()).isZero();
			});
	}

	@Test
	void transferWithAnotherAmountDoesNotMatchItsLedgerEntry() {
		ChainPayment other = new ChainPayment("0xabc", 0, 10, WALLET, RECIPIENT, BigInteger.ONE);

		assertThat(ReconciliationClassifier.classify(List.of(entry(PayoutStatus.SETTLED, ChainTransactionStatus.CONFIRMED)),
				List.of(other), HEAD, REQUIRED))
			.extracting(ReconciliationFinding::kind)
			.containsExactlyInAnyOrder(ReconciliationFinding.MISSING_CHAIN_EVENT,
					ReconciliationFinding.CHAIN_EVENT_WITHOUT_LEDGER);
	}

	@Test
	void sameTransferReadTwiceIsADuplicate() {
		assertThat(ReconciliationClassifier.classify(List.of(entry(PayoutStatus.SETTLED, ChainTransactionStatus.CONFIRMED)),
				List.of(payment(10), payment(10)), HEAD, REQUIRED))
			.singleElement()
			.extracting(ReconciliationFinding::kind)
			.isEqualTo(ReconciliationFinding.DUPLICATE_CHAIN_EVENT);
	}

	@Test
	void storedBlockThatDiffersFromTheChainIsAMismatch() {
		LedgerEntry entry = new LedgerEntry(UUID.randomUUID(), PayoutStatus.SETTLED, ChainTransactionStatus.CONFIRMED,
				"0xabc", 9L, WALLET, RECIPIENT, AMOUNT);

		assertThat(classify(entry, payment(10))).singleElement()
			.extracting(ReconciliationFinding::reason)
			.isEqualTo("STORED_BLOCK_NUMBER_MISMATCH");
	}

	@Test
	void settledPayoutWhoseChainTransactionIsNotConfirmedIsAMismatch() {
		assertThat(classify(entry(PayoutStatus.SETTLED, ChainTransactionStatus.PENDING), payment(10))).singleElement()
			.extracting(ReconciliationFinding::reason)
			.isEqualTo("SETTLED_WITHOUT_CONFIRMED_TRANSFER");
	}

	private static List<ReconciliationFinding> classify(LedgerEntry entry, ChainPayment payment) {
		return ReconciliationClassifier.classify(List.of(entry), List.of(payment), HEAD, REQUIRED);
	}

	private static LedgerEntry entry(PayoutStatus payoutStatus, ChainTransactionStatus chainStatus) {
		// 주소 대소문자가 달라도 같은 전송으로 본다
		return new LedgerEntry(UUID.randomUUID(), payoutStatus, chainStatus, "0xABC", null, WALLET.toUpperCase(), RECIPIENT,
				AMOUNT);
	}

	private static ChainPayment payment(long block) {
		return new ChainPayment("0xabc", 0, block, WALLET, RECIPIENT, AMOUNT);
	}

}
