package com.stablecoin.payout.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.stablecoin.TestcontainersConfiguration;

/**
 * 실제 Postgres에서 V6 스키마의 제약을 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ChainLedgerSchemaTest {

	private static final String SENDER = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void newChainTransactionStartsWithoutConfirmations() {
		UUID payoutId = payout();

		chainTransaction(payoutId, SENDER, 1, "CREATED");

		assertThat(jdbc.queryForObject("SELECT confirmations FROM chain_transactions WHERE payout_id = ?", Long.class,
				payoutId))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT updated_at IS NOT NULL FROM payouts WHERE id = ?", Boolean.class,
				payoutId))
			.isTrue();
	}

	@Test
	void payoutHasOnlyOneChainTransaction() {
		UUID payoutId = payout();
		chainTransaction(payoutId, SENDER, 1, "CREATED");

		assertThatThrownBy(() -> chainTransaction(payoutId, SENDER, 2, "CREATED"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void senderCannotUseTheSameNonceTwice() {
		chainTransaction(payout(), SENDER, 1, "CREATED");

		assertThatThrownBy(() -> chainTransaction(payout(), SENDER, 1, "CREATED"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownChainStatusIsRejected() {
		assertThatThrownBy(() -> chainTransaction(payout(), SENDER, 1, "BROADCAST"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void openFindingIsKeptOncePerPayoutAndKind() {
		UUID payoutId = payout();
		payoutFinding(payoutId, "STATUS_MISMATCH");
		payoutFinding(payoutId, "MISSING_CHAIN_EVENT");

		assertThatThrownBy(() -> payoutFinding(payoutId, "STATUS_MISMATCH"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void resolvedFindingAllowsTheSameProblemToBeOpenedAgain() {
		UUID payoutId = payout();
		payoutFinding(payoutId, "STATUS_MISMATCH");
		jdbc.update("UPDATE reconciliation_findings SET status = 'RESOLVED', resolved_at = now() WHERE payout_id = ?",
				payoutId);

		payoutFinding(payoutId, "STATUS_MISMATCH");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_findings WHERE payout_id = ?", Long.class,
				payoutId))
			.isEqualTo(2);
	}

	@Test
	void openFindingWithoutPayoutIsKeptOncePerChainEvent() {
		eventFinding("0xabc", 0);
		eventFinding("0xabc", 1);

		assertThatThrownBy(() -> eventFinding("0xabc", 0)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID payout() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO payouts (id, merchant_id, recipient_address, amount_wei, idempotency_key, request_fingerprint,
				  status) VALUES (?, 'merchant_demo_01', '0x70997970c51812dc3a010c7d01b50e0d17dc79c8', 1, ?, 'f',
				  'REQUESTED')""", id, "schema-" + id);
		return id;
	}

	private void chainTransaction(UUID payoutId, String sender, long nonce, String status) {
		jdbc.update("""
				INSERT INTO chain_transactions (id, payout_id, sender_address, recipient_address, amount_atomic, nonce,
				  status) VALUES (?, ?, ?, '0x70997970c51812dc3a010c7d01b50e0d17dc79c8', 1, ?, ?)""", UUID.randomUUID(),
				payoutId, sender, nonce, status);
	}

	private void payoutFinding(UUID payoutId, String kind) {
		jdbc.update("INSERT INTO reconciliation_findings (id, kind, payout_id) VALUES (?, ?, ?)", UUID.randomUUID(), kind,
				payoutId);
	}

	private void eventFinding(String hash, int logIndex) {
		jdbc.update("""
				INSERT INTO reconciliation_findings (id, kind, transaction_hash, log_index)
				VALUES (?, 'CHAIN_EVENT_WITHOUT_LEDGER', ?, ?)""", UUID.randomUUID(), hash, logIndex);
	}

}
