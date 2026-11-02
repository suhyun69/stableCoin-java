package com.stablecoin.payout.adapter.out.persistence;

import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.domain.Payout;

/**
 * JPA로 payouts 테이블에 저장한다. 서비스에 트랜잭션이 없으므로 save는 Spring Data의 트랜잭션으로 즉시 커밋된다.
 */
@Component
class PayoutPersistenceAdapter implements PayoutRepositoryPort {

	private final PayoutJpaRepository repository;

	PayoutPersistenceAdapter(PayoutJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Payout save(Payout payout) {
		return repository.save(new PayoutEntity(payout)).toDomain();
	}

}
