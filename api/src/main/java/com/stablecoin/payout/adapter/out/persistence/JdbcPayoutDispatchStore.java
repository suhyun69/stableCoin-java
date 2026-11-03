package com.stablecoin.payout.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.application.port.out.PayoutDispatchStorePort;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.PayoutDispatch;

/**
 * payout_dispatches 테이블. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 문장마다 바로 커밋된다.
 */
@Component
class JdbcPayoutDispatchStore implements PayoutDispatchStorePort {

	private static final String COLUMNS = "payout_id, status, from_address, nonce, tx_hash, signed_tx, updated_at";

	private final JdbcTemplate jdbc;

	JdbcPayoutDispatchStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean startOnce(UUID payoutId) {
		return jdbc.update("""
				INSERT INTO payout_dispatches (id, payout_id, status) VALUES (?, ?, 'STARTED')
				ON CONFLICT (payout_id) DO NOTHING""", UUID.randomUUID(), payoutId) == 1;
	}

	@Override
	public Optional<PayoutDispatch> find(UUID payoutId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM payout_dispatches WHERE payout_id = ?", this::map, payoutId)
			.stream()
			.findFirst();
	}

	@Override
	public Optional<PayoutDispatch> findForUpdate(UUID payoutId) {
		return jdbc
			.query("SELECT " + COLUMNS + " FROM payout_dispatches WHERE payout_id = ? FOR UPDATE", this::map, payoutId)
			.stream()
			.findFirst();
	}

	@Override
	public void markSigned(UUID payoutId, SignedTransaction tx) {
		int updated = jdbc.update("""
				UPDATE payout_dispatches
				SET status = 'SIGNED', from_address = ?, nonce = ?, tx_hash = ?, signed_tx = ?, updated_at = now()
				WHERE payout_id = ? AND status = 'STARTED'""", tx.from(), tx.nonce(), tx.hash(), tx.raw(), payoutId);
		if (updated != 1) {
			throw new IllegalStateException("payout dispatch " + payoutId + " is not STARTED");
		}
	}

	@Override
	public boolean transition(UUID payoutId, DispatchStatus expected, DispatchStatus next, String error) {
		return jdbc.update("""
				UPDATE payout_dispatches SET status = ?, last_error = COALESCE(?, last_error), updated_at = now()
				WHERE payout_id = ? AND status = ?""", next.name(), error, payoutId, expected.name()) == 1;
	}

	@Override
	public long nextNonceFloor(String fromAddress) {
		Long max = jdbc.queryForObject("""
				SELECT max(nonce) FROM payout_dispatches WHERE status = 'SIGNED' AND lower(from_address) = lower(?)""",
				Long.class, fromAddress);
		return (max == null) ? 0 : max + 1;
	}

	@Override
	public List<PayoutDispatch> findByStatus(DispatchStatus status, int limit) {
		return jdbc.query("SELECT " + COLUMNS
				+ " FROM payout_dispatches WHERE status = ? ORDER BY updated_at LIMIT ?", this::map, status.name(), limit);
	}

	@Override
	public List<PayoutDispatch> findStuck(Duration olderThan, int limit) {
		return jdbc.query("SELECT " + COLUMNS + """
				 FROM payout_dispatches
				WHERE status IN ('STARTED', 'SIGNED') AND updated_at < now() - make_interval(secs => ?)
				ORDER BY updated_at LIMIT ?""", this::map, olderThan.toMillis() / 1000.0, limit);
	}

	private PayoutDispatch map(ResultSet rs, int row) throws SQLException {
		Timestamp updatedAt = rs.getTimestamp("updated_at");
		long nonce = rs.getLong("nonce");
		return new PayoutDispatch(rs.getObject("payout_id", UUID.class), DispatchStatus.valueOf(rs.getString("status")),
				rs.getString("from_address"), rs.wasNull() ? null : nonce, rs.getString("tx_hash"),
				rs.getString("signed_tx"), (updatedAt != null) ? updatedAt.toInstant() : null);
	}

}
