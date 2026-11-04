package com.stablecoin.payout.adapter.out.persistence;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.PayoutDispatchStorePort;

/**
 * payout_dispatches 테이블. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 문장마다 바로 커밋된다.
 */
@Component
class JdbcPayoutDispatchStore implements PayoutDispatchStorePort {

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
	public boolean lock(UUID payoutId) {
		return !jdbc.queryForList("SELECT payout_id FROM payout_dispatches WHERE payout_id = ? FOR UPDATE", UUID.class,
				payoutId)
			.isEmpty();
	}

	@Override
	public List<UUID> findStuck(Duration olderThan, int limit) {
		return jdbc.queryForList("""
				SELECT d.payout_id
				FROM payout_dispatches d
				JOIN payouts p ON p.id = d.payout_id
				LEFT JOIN chain_transactions ct ON ct.payout_id = d.payout_id
				WHERE p.status = 'REQUESTED'
				  AND (ct.id IS NULL OR ct.status IN ('SIGNED', 'BROADCAST_UNKNOWN'))
				  AND COALESCE(ct.signed_at, d.created_at) < now() - make_interval(secs => ?)
				ORDER BY COALESCE(ct.signed_at, d.created_at) LIMIT ?""", UUID.class, olderThan.toMillis() / 1000.0,
				limit);
	}

}
