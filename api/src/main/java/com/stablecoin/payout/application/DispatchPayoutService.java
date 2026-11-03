package com.stablecoin.payout.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.stablecoin.chain.application.ChainException;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.application.port.in.DispatchPayoutUseCase;
import com.stablecoin.payout.application.port.out.PayoutChainPort;
import com.stablecoin.payout.application.port.out.PayoutDispatchStorePort;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.application.port.out.ProcessedMessagePort;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutDispatch;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 지급을 체인으로 보낸다 (PAYOUT_REQUESTED 소비자).
 * <ol>
 * <li><b>한 번만 시작:</b> 받은 메시지를 inbox(processed_messages)에 기록하고, 같은 트랜잭션에서 payout_dispatches 에
 * 지급마다 한 행만 만든다 (지급 ID UNIQUE). 같은 메시지를 다시 받으면 건너뛰고, 메시지 ID가 달라도 전송은 한 번만 진행한다.</li>
 * <li><b>보내기 전에 저장:</b> 서명한 트랜잭션(raw, hash, nonce)을 먼저 DB에 커밋(SIGNED)한 뒤 노드에 보낸다.
 * 보내다가 죽어도 같은 서명 트랜잭션을 다시 보내면 되고, 같은 nonce라 두 번 나가지 않는다.</li>
 * <li><b>보낸 뒤 기록:</b> 노드가 받으면 지급 AWAITING_CONFIRMATION, 전송 기록 BROADCAST.</li>
 * </ol>
 * 서명할 때 노드가 거부하면(잔액 부족 등) 아무것도 보내지 않았으므로 FAILED, 보낼 때 거부하면 결과를 장담할 수 없어
 * REQUIRES_REVIEW. 노드에 연결할 수 없으면 예외를 그대로 던져 소비자가 다시 시도하게 하고, 멈춘 전송은 resumeStuck 이 이어간다.
 */
@Service
public class DispatchPayoutService implements DispatchPayoutUseCase {

	private static final Logger log = LoggerFactory.getLogger(DispatchPayoutService.class);

	private static final int RESUME_BATCH = 20;

	/** processed_messages.consumer_name */
	static final String CONSUMER_NAME = "payout-requested";

	private final PayoutRepositoryPort payouts;

	private final PayoutDispatchStorePort dispatches;

	private final PayoutChainPort chain;

	private final ProcessedMessagePort inbox;

	private final PayoutSettlementService settlement;

	private final TransactionTemplate transaction;

	private final Duration stuckAfter;

	public DispatchPayoutService(PayoutRepositoryPort payouts, PayoutDispatchStorePort dispatches, PayoutChainPort chain,
			ProcessedMessagePort inbox, PayoutSettlementService settlement,
			PlatformTransactionManager transactionManager,
			@Value("${payout.dispatcher.stuck-after:30s}") Duration stuckAfter) {
		this.payouts = payouts;
		this.dispatches = dispatches;
		this.chain = chain;
		this.inbox = inbox;
		this.settlement = settlement;
		this.transaction = new TransactionTemplate(transactionManager);
		this.stuckAfter = stuckAfter;
	}

	@Override
	public void dispatch(UUID payoutId, String messageId) {
		Payout payout = requested(payoutId);
		if (payout == null) {
			return;
		}
		Boolean firstDelivery = transaction.execute(status -> {
			if (!inbox.markProcessed(CONSUMER_NAME, messageId, payoutId)) {
				return false;
			}
			dispatches.startOnce(payoutId);
			return true;
		});
		if (!Boolean.TRUE.equals(firstDelivery)) {
			// 이미 처리한 메시지. 그때 멈춘 전송이 있으면 재개 작업이 이어간다.
			log.info("Message {} for payout {} was already processed; skipping", messageId, payoutId);
			return;
		}
		send(payout);
	}

	@Override
	public void dispatch(UUID payoutId) {
		Payout payout = requested(payoutId);
		if (payout == null) {
			return;
		}
		dispatches.startOnce(payoutId);
		send(payout);
	}

	/**
	 * 아직 보내지 않은(REQUESTED) 지급이면 돌려주고, 없거나 이미 처리됐으면 null.
	 */
	private Payout requested(UUID payoutId) {
		Payout payout = payouts.findById(payoutId).orElse(null);
		if (payout == null) {
			log.warn("Payout {} not found; skipping dispatch", payoutId);
			return null;
		}
		if (payout.status() != PayoutStatus.REQUESTED) {
			log.debug("Payout {} is already {}; skipping dispatch", payoutId, payout.status());
			return null;
		}
		return payout;
	}

	private void send(Payout payout) {
		chain.withPayoutWalletLock(() -> {
			sendLocked(payout);
			return null;
		});
	}

	/**
	 * 지급 지갑 잠금 안에서: 서명(필요하면) → 저장 → 노드에 보냄 → 기록.
	 */
	private void sendLocked(Payout payout) {
		UUID payoutId = payout.id();
		SignedTransaction signed;
		try {
			signed = transaction.execute(status -> signIfNeeded(payout));
		}
		catch (ChainException ex) {
			settlement.rejectBeforeSend(payoutId, ex.getMessage());
			return;
		}
		if (signed == null) {
			return;
		}
		try {
			chain.broadcast(signed);
		}
		catch (ChainException ex) {
			log.error("Node rejected signed payout transaction {} for payout {}: {}", signed.hash(), payoutId,
					ex.getMessage());
			settlement.requireReview(payoutId, PayoutStatus.REQUESTED, DispatchStatus.SIGNED, "BROADCAST_REJECTED",
					ex.getMessage());
			return;
		}
		settlement.markBroadcast(payoutId, signed.hash());
	}

	/**
	 * 전송 기록을 잠그고, STARTED 면 서명해서 SIGNED 로 저장한다. 이미 SIGNED 면 저장된 트랜잭션을 그대로 돌려준다.
	 * 그 밖의 상태(이미 보냄 등)면 null.
	 */
	private SignedTransaction signIfNeeded(Payout payout) {
		PayoutDispatch dispatch = dispatches.findForUpdate(payout.id()).orElseThrow();
		return switch (dispatch.status()) {
			case STARTED -> {
				long minNonce = dispatches.nextNonceFloor(chain.payoutWallet());
				SignedTransaction signed = chain.sign(payout.recipientAddress(), payout.amountWei(), minNonce);
				dispatches.markSigned(payout.id(), signed);
				log.info("Signed payout {} as {} (nonce {})", payout.id(), signed.hash(), signed.nonce());
				yield signed;
			}
			case SIGNED -> new SignedTransaction(dispatch.fromAddress(), payout.recipientAddress(), payout.amountWei(),
					dispatch.nonce(), dispatch.signedTx(), dispatch.txHash());
			default -> null;
		};
	}

	@Override
	public int resumeStuck() {
		List<PayoutDispatch> stuck = dispatches.findStuck(stuckAfter, RESUME_BATCH);
		for (PayoutDispatch dispatch : stuck) {
			log.info("Resuming payout {} stuck in {}", dispatch.payoutId(), dispatch.status());
			dispatch(dispatch.payoutId());
		}
		return stuck.size();
	}

}
