package com.stablecoin.payout.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.payout.application.port.in.ConfirmPayoutsUseCase;
import com.stablecoin.payout.application.port.out.PayoutChainPort;
import com.stablecoin.payout.application.port.out.PayoutDispatchStorePort;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.PayoutDispatch;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 노드에 보낸(BROADCAST) 지급 트랜잭션의 영수증을 확인한다.
 * <ul>
 * <li>성공 → 정산 (SETTLED, 가맹점 알림 MERCHANT_PAYOUT_SETTLED)</li>
 * <li>실패(revert) → FAILED (가맹점 알림 MERCHANT_PAYOUT_FAILED)</li>
 * <li>review-after 가 지나도 영수증이 없음 → REQUIRES_REVIEW (돈이 나갔는지 사람이 확인)</li>
 * </ul>
 */
@Service
public class ConfirmPayoutsService implements ConfirmPayoutsUseCase {

	private static final Logger log = LoggerFactory.getLogger(ConfirmPayoutsService.class);

	private static final int BATCH = 50;

	private final PayoutDispatchStorePort dispatches;

	private final PayoutChainPort chain;

	private final PayoutSettlementService settlement;

	private final Duration reviewAfter;

	public ConfirmPayoutsService(PayoutDispatchStorePort dispatches, PayoutChainPort chain,
			PayoutSettlementService settlement, @Value("${payout.confirmation.review-after:10m}") Duration reviewAfter) {
		this.dispatches = dispatches;
		this.chain = chain;
		this.settlement = settlement;
		this.reviewAfter = reviewAfter;
	}

	@Override
	public int confirmBroadcast() {
		int changed = 0;
		for (PayoutDispatch dispatch : dispatches.findByStatus(DispatchStatus.BROADCAST, BATCH)) {
			if (confirm(dispatch)) {
				changed++;
			}
		}
		return changed;
	}

	private boolean confirm(PayoutDispatch dispatch) {
		Optional<ChainReceipt> receipt = chain.receipt(dispatch.txHash());
		if (receipt.isPresent()) {
			return receipt.get().success() ? settlement.settle(dispatch.payoutId(), dispatch.txHash())
					: settlement.failOnChain(dispatch.payoutId(), dispatch.txHash());
		}
		if (dispatch.updatedAt() != null && dispatch.updatedAt().plus(reviewAfter).isBefore(Instant.now())) {
			log.warn("Payout {} transaction {} has no receipt after {}; requires review", dispatch.payoutId(),
					dispatch.txHash(), reviewAfter);
			return settlement.requireReview(dispatch.payoutId(), PayoutStatus.AWAITING_CONFIRMATION,
					DispatchStatus.BROADCAST, "CONFIRMATION_TIMEOUT", "no receipt after " + reviewAfter);
		}
		return false;
	}

}
