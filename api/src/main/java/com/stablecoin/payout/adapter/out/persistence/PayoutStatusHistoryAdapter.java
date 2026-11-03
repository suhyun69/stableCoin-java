package com.stablecoin.payout.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.out.PayoutStatusHistoryPort;
import com.stablecoin.payout.domain.PayoutStatus;

@Component
class PayoutStatusHistoryAdapter implements PayoutStatusHistoryPort {

	private final PayoutStatusHistoryJpaRepository repository;

	PayoutStatusHistoryAdapter(PayoutStatusHistoryJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public void record(UUID payoutId, PayoutStatus from, PayoutStatus to, String reason) {
		repository.save(new PayoutStatusHistoryEntity(payoutId, from, to, reason));
	}

}
