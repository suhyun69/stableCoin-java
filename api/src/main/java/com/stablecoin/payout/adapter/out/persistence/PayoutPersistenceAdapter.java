package com.stablecoin.payout.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.domain.Payout;

/**
 * JPA로 payouts 테이블에 저장한다. 호출한 쪽의 트랜잭션에 참여한다.
 */
@Component
class PayoutPersistenceAdapter implements PayoutRepositoryPort {

	private final PayoutJpaRepository repository;

	PayoutPersistenceAdapter(PayoutJpaRepository repository) {
		this.repository = repository;
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

}
