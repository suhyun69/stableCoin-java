package com.stablecoin.payout.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.stablecoin.payout.application.port.in.GetPayoutUseCase;
import com.stablecoin.payout.application.port.out.ChainTransactionStorePort;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.domain.Payout;

@Service
public class GetPayoutService implements GetPayoutUseCase {

	private final PayoutRepositoryPort payouts;

	private final ChainTransactionStorePort chainTransactions;

	public GetPayoutService(PayoutRepositoryPort payouts, ChainTransactionStorePort chainTransactions) {
		this.payouts = payouts;
		this.chainTransactions = chainTransactions;
	}

	@Override
	public PayoutView get(UUID payoutId) {
		Payout payout = payouts.findById(payoutId).orElseThrow(() -> new PayoutNotFoundException(payoutId));
		return new PayoutView(payout, chainTransactions.findByPayoutId(payoutId).orElse(null));
	}

}
