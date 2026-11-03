package com.stablecoin.payout.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.payout.application.port.in.ConfirmPayoutsUseCase;
import com.stablecoin.payout.application.port.out.PayoutChainPort;
import com.stablecoin.payout.application.port.out.PayoutDispatchStorePort;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutDispatch;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 노드에 보낸(BROADCAST) 지급 트랜잭션의 영수증을 확인한다.
 * <ul>
 * <li>영수증 블록 위로 required-confirmations 개 블록이 쌓일 때까지(영수증 블록 포함) 기다린다. 그 전에 블록 재구성으로
 * 영수증이 사라지면 다시 영수증을 기다린다.</li>
 * <li>성공 → 노드에 기록된 트랜잭션의 보낸 지갑, 받는 지갑, 금액이 지급 내용과 같으면 정산
 * (SETTLED, 가맹점 알림 MERCHANT_PAYOUT_SETTLED). 다르면 REQUIRES_REVIEW.</li>
 * <li>실패(revert) → FAILED (가맹점 알림 MERCHANT_PAYOUT_FAILED)</li>
 * <li>영수증이 없는데 지급 지갑의 그 nonce가 이미 다른 트랜잭션으로 블록에 들어감 → 이 트랜잭션은 영영 들어갈 수 없으므로
 * review-after 를 기다리지 않고 바로 REQUIRES_REVIEW (NONCE_CONFLICT). 지급 밖에서 같은 지갑을 쓴 경우에 생긴다.</li>
 * <li>review-after 가 지나도 영수증이 없음 → REQUIRES_REVIEW (돈이 나갔는지 사람이 확인)</li>
 * </ul>
 */
@Service
public class ConfirmPayoutsService implements ConfirmPayoutsUseCase {

	private static final Logger log = LoggerFactory.getLogger(ConfirmPayoutsService.class);

	private static final int BATCH = 50;

	private final PayoutRepositoryPort payouts;

	private final PayoutDispatchStorePort dispatches;

	private final PayoutChainPort chain;

	private final PayoutSettlementService settlement;

	private final Duration reviewAfter;

	private final int requiredConfirmations;

	public ConfirmPayoutsService(PayoutRepositoryPort payouts, PayoutDispatchStorePort dispatches,
			PayoutChainPort chain, PayoutSettlementService settlement,
			@Value("${payout.confirmation.review-after:10m}") Duration reviewAfter,
			@Value("${payout.confirmation.required-confirmations:1}") int requiredConfirmations) {
		if (requiredConfirmations < 1) {
			throw new IllegalArgumentException("payout.confirmation.required-confirmations must be at least 1");
		}
		this.payouts = payouts;
		this.dispatches = dispatches;
		this.chain = chain;
		this.settlement = settlement;
		this.reviewAfter = reviewAfter;
		this.requiredConfirmations = requiredConfirmations;
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
		if (receipt.isEmpty() && nonceTakenByAnotherTransaction(dispatch)) {
			// 확정 nonce를 읽는 사이에 이 트랜잭션이 블록에 들어갔을 수 있으므로 한 번 더 본다
			receipt = chain.receipt(dispatch.txHash());
			if (receipt.isEmpty()) {
				String detail = "nonce " + dispatch.nonce() + " of " + dispatch.fromAddress()
						+ " was used by another transaction";
				log.error("Payout {} transaction {} can never be mined: {}", dispatch.payoutId(), dispatch.txHash(),
						detail);
				return settlement.requireReview(dispatch.payoutId(), PayoutStatus.AWAITING_CONFIRMATION,
						DispatchStatus.BROADCAST, "NONCE_CONFLICT", detail);
			}
		}
		if (receipt.isPresent()) {
			long confirmations = chain.latestBlockNumber() - receipt.get().blockNumber() + 1;
			if (confirmations < requiredConfirmations) {
				log.debug("Payout {} transaction {} has {}/{} confirmations", dispatch.payoutId(), dispatch.txHash(),
						confirmations, requiredConfirmations);
				return false;
			}
			if (!receipt.get().success()) {
				return settlement.failOnChain(dispatch.payoutId(), dispatch.txHash());
			}
			String mismatch = mismatch(dispatch);
			if (mismatch != null) {
				log.error("Payout {} transaction {} does not match the payout: {}", dispatch.payoutId(),
						dispatch.txHash(), mismatch);
				return settlement.requireReview(dispatch.payoutId(), PayoutStatus.AWAITING_CONFIRMATION,
						DispatchStatus.BROADCAST, "TRANSFER_MISMATCH", mismatch);
			}
			return settlement.settle(dispatch.payoutId(), dispatch.txHash());
		}
		if (dispatch.updatedAt() != null && dispatch.updatedAt().plus(reviewAfter).isBefore(Instant.now())) {
			log.warn("Payout {} transaction {} has no receipt after {}; requires review", dispatch.payoutId(),
					dispatch.txHash(), reviewAfter);
			return settlement.requireReview(dispatch.payoutId(), PayoutStatus.AWAITING_CONFIRMATION,
					DispatchStatus.BROADCAST, "CONFIRMATION_TIMEOUT", "no receipt after " + reviewAfter);
		}
		return false;
	}

	/**
	 * 보낸 지갑에서 블록에 들어간 트랜잭션 수가 이 nonce보다 크면, 이 nonce 자리는 이미 쓰였다.
	 */
	private boolean nonceTakenByAnotherTransaction(PayoutDispatch dispatch) {
		return dispatch.nonce() != null && dispatch.fromAddress() != null
				&& chain.confirmedNonce(dispatch.fromAddress()) > dispatch.nonce();
	}

	/**
	 * 노드에 기록된 트랜잭션이 지급 내용(지급 지갑 → 받는 지갑, 금액)과 다르면 그 이유, 같으면 null.
	 */
	private String mismatch(PayoutDispatch dispatch) {
		Payout payout = payouts.findById(dispatch.payoutId()).orElse(null);
		if (payout == null) {
			return "payout not found";
		}
		ChainTransaction tx = chain.transaction(dispatch.txHash()).orElse(null);
		if (tx == null) {
			return "transaction not found on the node";
		}
		if (!sameAddress(tx.from(), dispatch.fromAddress())) {
			return "sent from " + tx.from() + ", expected " + dispatch.fromAddress();
		}
		if (!sameAddress(tx.to(), payout.recipientAddress())) {
			return "sent to " + tx.to() + ", expected " + payout.recipientAddress();
		}
		if (!payout.amountWei().equals(tx.value())) {
			return "sent " + tx.value() + " wei, expected " + payout.amountWei();
		}
		return null;
	}

	private static boolean sameAddress(String a, String b) {
		return a != null && b != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
	}

}
