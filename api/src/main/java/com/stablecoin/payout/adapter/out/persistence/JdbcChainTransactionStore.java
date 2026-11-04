package com.stablecoin.payout.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.application.port.out.ChainTransactionStorePort;
import com.stablecoin.payout.domain.ChainTransactionStatus;
import com.stablecoin.payout.domain.PayoutChainTransaction;

/**
 * chain_transactions 테이블. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 문장마다 바로 커밋된다.
 */
@Component
class JdbcChainTransactionStore implements ChainTransactionStorePort {

	private static final String COLUMNS = """
			ct.payout_id, ct.status, ct.sender_address, ct.nonce, ct.transaction_hash, ct.raw_transaction, ct.last_error,
			ct.included_block_number, ct.confirmations, ct.broadcast_at""";

	private final JdbcTemplate jdbc;

	JdbcChainTransactionStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<PayoutChainTransaction> findByPayoutId(UUID payoutId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM chain_transactions ct WHERE ct.payout_id = ?", this::map, payoutId)
			.stream()
			.findFirst();
	}

	@Override
	public void saveSigned(UUID payoutId, SignedTransaction tx) {
		jdbc.update("""
				INSERT INTO chain_transactions (id, payout_id, sender_address, recipient_address, amount_atomic, nonce,
				  status, transaction_hash, raw_transaction, signed_at)
				VALUES (?, ?, ?, ?, ?, ?, 'SIGNED', ?, ?, now())""", UUID.randomUUID(), payoutId, tx.from(), tx.to(),
				new BigDecimal(tx.value()), tx.nonce(), tx.hash(), tx.raw());
	}

	@Override
	public void lockWalletForSigning(String senderAddress) {
		// 트랜잭션 단위 advisory lock: 커밋·롤백할 때 저절로 풀리고, 테이블이 따로 필요 없다
		jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(lower(?), 0))", rs -> null, senderAddress);
	}

	@Override
	public long nextNonceFloor(String senderAddress) {
		Long max = jdbc.queryForObject("""
				SELECT max(nonce) FROM chain_transactions
				WHERE status IN ('SIGNED', 'BROADCAST_UNKNOWN') AND lower(sender_address) = lower(?)""", Long.class,
				senderAddress);
		return (max == null) ? 0 : max + 1;
	}

	@Override
	public void markPending(UUID payoutId) {
		jdbc.update("""
				UPDATE chain_transactions SET status = 'PENDING', broadcast_at = now(), last_error = NULL
				WHERE payout_id = ? AND status IN ('SIGNED', 'BROADCAST_UNKNOWN')""", payoutId);
	}

	@Override
	public void markBroadcastUnknown(UUID payoutId, String error) {
		jdbc.update("""
				UPDATE chain_transactions SET status = 'BROADCAST_UNKNOWN', last_error = ?
				WHERE payout_id = ? AND status IN ('SIGNED', 'BROADCAST_UNKNOWN')""", error, payoutId);
	}

	@Override
	public void recordIncluded(UUID payoutId, long blockNumber, long confirmations, boolean success) {
		jdbc.update("""
				UPDATE chain_transactions
				SET status = CASE WHEN ? THEN 'MINED_SUCCESS' ELSE status END, included_block_number = ?,
				    confirmations = ?, mined_at = COALESCE(mined_at, now())
				WHERE payout_id = ? AND status IN ('PENDING', 'MINED_SUCCESS')""", success, blockNumber, confirmations,
				payoutId);
	}

	@Override
	public void markNotIncluded(UUID payoutId) {
		jdbc.update("""
				UPDATE chain_transactions
				SET status = 'PENDING', included_block_number = NULL, confirmations = 0, mined_at = NULL
				WHERE payout_id = ? AND status IN ('PENDING', 'MINED_SUCCESS')""", payoutId);
	}

	@Override
	public void markConfirmed(UUID payoutId) {
		jdbc.update("""
				UPDATE chain_transactions SET status = 'CONFIRMED', confirmed_at = now()
				WHERE payout_id = ? AND status IN ('PENDING', 'MINED_SUCCESS')""", payoutId);
	}

	@Override
	public void markReverted(UUID payoutId) {
		jdbc.update("""
				UPDATE chain_transactions SET status = 'MINED_REVERTED', last_error = 'transaction reverted'
				WHERE payout_id = ? AND status IN ('PENDING', 'MINED_SUCCESS')""", payoutId);
	}

	@Override
	public void markFailed(UUID payoutId, String error) {
		jdbc.update("UPDATE chain_transactions SET status = 'FAILED', last_error = ? WHERE payout_id = ?", error,
				payoutId);
	}

	@Override
	public void recordError(UUID payoutId, String error) {
		jdbc.update("UPDATE chain_transactions SET last_error = ? WHERE payout_id = ?", error, payoutId);
	}

	@Override
	public List<PayoutChainTransaction> findAwaitingConfirmation(int limit) {
		return jdbc.query("SELECT " + COLUMNS + """
				 FROM chain_transactions ct
				JOIN payouts p ON p.id = ct.payout_id
				WHERE p.status = 'AWAITING_CONFIRMATION' AND ct.status IN ('PENDING', 'MINED_SUCCESS')
				ORDER BY ct.broadcast_at LIMIT ?""", this::map, limit);
	}

	private PayoutChainTransaction map(ResultSet rs, int row) throws SQLException {
		long block = rs.getLong("included_block_number");
		boolean notIncluded = rs.wasNull();
		Timestamp broadcastAt = rs.getTimestamp("broadcast_at");
		return new PayoutChainTransaction(rs.getObject("payout_id", UUID.class),
				ChainTransactionStatus.valueOf(rs.getString("status")), rs.getString("sender_address"),
				rs.getLong("nonce"), rs.getString("transaction_hash"), rs.getString("raw_transaction"),
				rs.getString("last_error"), notIncluded ? null : block, rs.getLong("confirmations"),
				(broadcastAt != null) ? broadcastAt.toInstant() : null);
	}

}
