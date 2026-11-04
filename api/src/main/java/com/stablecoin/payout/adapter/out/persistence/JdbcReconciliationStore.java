package com.stablecoin.payout.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.ReconciliationStorePort;
import com.stablecoin.payout.domain.ChainTransactionStatus;
import com.stablecoin.payout.domain.LedgerEntry;
import com.stablecoin.payout.domain.PayoutStatus;
import com.stablecoin.payout.domain.ReconciliationFinding;

/**
 * 장부 읽기와 reconciliation_findings 테이블. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 문장마다 바로 커밋된다.
 */
@Component
class JdbcReconciliationStore implements ReconciliationStorePort {

	private final JdbcTemplate jdbc;

	private final JsonMapper jsonMapper;

	JdbcReconciliationStore(JdbcTemplate jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public List<LedgerEntry> readLedger(long fromBlock, long toBlock, Collection<String> transactionHashes) {
		String[] hashes = transactionHashes.stream().map(hash -> hash.toLowerCase(Locale.ROOT)).toArray(String[]::new);
		return jdbc.query(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
					SELECT p.id, p.status AS payout_status, ct.status AS chain_status, ct.transaction_hash,
					       ct.included_block_number, ct.sender_address, p.recipient_address, p.amount_wei
					FROM payouts p
					JOIN chain_transactions ct ON ct.payout_id = p.id
					WHERE ct.included_block_number BETWEEN ? AND ? OR lower(ct.transaction_hash) = ANY(?)""");
			statement.setLong(1, fromBlock);
			statement.setLong(2, toBlock);
			statement.setArray(3, connection.createArrayOf("text", hashes));
			return statement;
		}, this::mapLedger);
	}

	@Override
	public void recordOpen(ReconciliationFinding finding) {
		jdbc.update("""
				INSERT INTO reconciliation_findings (id, kind, payout_id, transaction_hash, log_index, detail)
				VALUES (?, ?, ?, ?, ?, ?::jsonb)
				ON CONFLICT DO NOTHING""", UUID.randomUUID(), finding.kind(), finding.payoutId(),
				finding.transactionHash(), finding.logIndex(), detail(finding));
	}

	@Override
	public void recordResolved(ReconciliationFinding finding) {
		jdbc.update("""
				INSERT INTO reconciliation_findings (id, kind, payout_id, transaction_hash, log_index, detail, status,
				  resolved_at)
				VALUES (?, ?, ?, ?, ?, ?::jsonb, 'RESOLVED', now())""", UUID.randomUUID(), finding.kind(),
				finding.payoutId(), finding.transactionHash(), finding.logIndex(), detail(finding));
	}

	private String detail(ReconciliationFinding finding) {
		Map<String, Object> detail = new LinkedHashMap<>();
		if (finding.blockNumber() != null) {
			detail.put("blockNumber", finding.blockNumber());
		}
		if (finding.confirmations() != null) {
			detail.put("confirmations", finding.confirmations());
		}
		if (finding.reason() != null) {
			detail.put("reason", finding.reason());
		}
		return jsonMapper.writeValueAsString(detail);
	}

	private LedgerEntry mapLedger(ResultSet rs, int row) throws SQLException {
		long block = rs.getLong("included_block_number");
		boolean notIncluded = rs.wasNull();
		return new LedgerEntry(rs.getObject("id", UUID.class), PayoutStatus.valueOf(rs.getString("payout_status")),
				ChainTransactionStatus.valueOf(rs.getString("chain_status")), rs.getString("transaction_hash"),
				notIncluded ? null : block, rs.getString("sender_address"), rs.getString("recipient_address"),
				rs.getBigDecimal("amount_wei").toBigIntegerExact());
	}

}
