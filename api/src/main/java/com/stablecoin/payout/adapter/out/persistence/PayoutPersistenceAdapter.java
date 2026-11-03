package com.stablecoin.payout.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * payouts 테이블. 저장과 조회는 JPA, 상태 변경은 조건부 UPDATE(JDBC)로 한다. 호출한 쪽의 트랜잭션에 참여한다.
 */
@Component
class PayoutPersistenceAdapter implements PayoutRepositoryPort {

	private final PayoutJpaRepository repository;

	private final JdbcTemplate jdbc;

	PayoutPersistenceAdapter(PayoutJpaRepository repository, JdbcTemplate jdbc) {
		this.repository = repository;
		this.jdbc = jdbc;
	}

	/**
	 * 바로 flush해서 멱등성 키 UNIQUE 위반을 이 호출에서 드러낸다 (커밋 시점까지 미루지 않는다).
	 */
	@Override
	public Payout save(Payout payout) {
		return repository.saveAndFlush(new PayoutEntity(payout)).toDomain();
	}

	@Override
	public Optional<Payout> findByIdempotencyKey(String idempotencyKey) {
		return repository.findByIdempotencyKey(idempotencyKey).map(PayoutEntity::toDomain);
	}

	@Override
	public Optional<Payout> findById(UUID id) {
		return repository.findById(id).map(PayoutEntity::toDomain);
	}

	@Override
	public boolean transition(UUID id, PayoutStatus expected, PayoutStatus next, String transactionHash) {
		return jdbc.update("""
				UPDATE payouts SET status = ?, transaction_hash = COALESCE(?, transaction_hash)
				WHERE id = ? AND status = ?""", next.name(), transactionHash, id, expected.name()) == 1;
	}

	@Override
	public Optional<String> findTransactionHash(UUID id) {
		return jdbc.queryForList("SELECT transaction_hash FROM payouts WHERE id = ?", String.class, id)
			.stream()
			.filter(java.util.Objects::nonNull)
			.findFirst();
	}

}
