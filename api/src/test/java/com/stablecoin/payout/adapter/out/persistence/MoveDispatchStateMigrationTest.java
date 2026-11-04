package com.stablecoin.payout.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stablecoin.TestcontainersConfiguration;

/**
 * V6 까지의 데이터를 만들고 V7 을 적용해, 전송 기록이 chain_transactions 로 옮겨지는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MoveDispatchStateMigrationTest {

	private static final String SCHEMA = "move_dispatch_test";

	private static final String WALLET = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void dropSchema() {
		jdbc.update("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
	}

	@Test
	void movesSignedDispatchesAndLeavesOnlyTheStartedMark() {
		migrateTo("6");
		UUID started = dispatch("STARTED", "REQUESTED", null, null);
		UUID signed = dispatch("SIGNED", "REQUESTED", 1L, "0xsigned");
		UUID broadcast = dispatch("BROADCAST", "AWAITING_CONFIRMATION", 2L, "0xbroadcast");
		UUID confirmed = dispatch("CONFIRMED", "SETTLED", 3L, "0xconfirmed");
		UUID reverted = dispatch("FAILED", "FAILED", 4L, "0xreverted");
		UUID rejectedBeforeSend = dispatch("FAILED", "FAILED", null, null);
		UUID review = dispatch("REQUIRES_REVIEW", "REQUIRES_REVIEW", 5L, "0xreview");

		migrateTo("7");

		assertThat(chainStatus(signed)).containsEntry("status", "SIGNED").containsEntry("transaction_hash", "0xsigned");
		assertThat(chainStatus(broadcast)).containsEntry("status", "PENDING");
		assertThat(chainStatus(confirmed)).containsEntry("status", "CONFIRMED");
		assertThat(chainStatus(reverted)).containsEntry("status", "MINED_REVERTED");
		assertThat(chainStatus(review)).containsEntry("status", "PENDING");
		assertThat(count("chain_transactions WHERE payout_id IN ('" + started + "', '" + rejectedBeforeSend + "')"))
			.isZero();
		assertThat(count("payout_dispatches")).isEqualTo(7);
		assertThat(count("payout_dispatches WHERE status = 'STARTED'")).isEqualTo(7);
	}

	@Test
	void keepsOnlyTheNewestRecordOfANonceUsedAgainAfterANodeRestart() {
		migrateTo("6");
		dispatch("CONFIRMED", "SETTLED", 0L, "0xold");
		jdbc.update("UPDATE " + SCHEMA + ".payout_dispatches SET updated_at = now() - interval '1 day'");
		UUID newest = dispatch("CONFIRMED", "SETTLED", 0L, "0xnew");

		migrateTo("7");

		assertThat(count("chain_transactions")).isEqualTo(1);
		assertThat(chainStatus(newest)).containsEntry("transaction_hash", "0xnew");
	}

	private void migrateTo(String version) {
		Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target(version).load().migrate();
	}

	private UUID dispatch(String status, String payoutStatus, Long nonce, String hash) {
		UUID payoutId = UUID.randomUUID();
		jdbc.update("INSERT INTO " + SCHEMA + """
				.payouts (id, merchant_id, recipient_address, amount_wei, idempotency_key, request_fingerprint, status)
				VALUES (?, 'merchant_demo_01', '0x70997970c51812dc3a010c7d01b50e0d17dc79c8', 1, ?, 'f', ?)""", payoutId,
				"key-" + payoutId, payoutStatus);
		jdbc.update("INSERT INTO " + SCHEMA + """
				.payout_dispatches (id, payout_id, status, from_address, nonce, tx_hash, signed_tx)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), payoutId, status, (nonce != null) ? WALLET : null, nonce,
				hash, (hash != null) ? "0x02raw" : null);
		return payoutId;
	}

	private Map<String, Object> chainStatus(UUID payoutId) {
		return jdbc.queryForMap("SELECT status, transaction_hash FROM " + SCHEMA + ".chain_transactions WHERE payout_id = ?",
				payoutId);
	}

	private long count(String from) {
		return jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + "." + from, Long.class);
	}

}
