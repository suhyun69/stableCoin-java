package com.stablecoin.payout.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.stablecoin.outbox.application.port.out.OutboxPort;
import com.stablecoin.outbox.domain.OutboxMessage;
import com.stablecoin.payout.application.port.out.PayoutDispatchStorePort;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.application.port.out.PayoutStatusHistoryPort;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 지급 상태 변경을 한곳에서 처리한다. 지급 상태, 상태 이력, 전송 기록, (필요하면) 가맹점 알림 outbox 메시지를
 * <b>한 DB 트랜잭션</b>으로 바꾼다 (stableCoin TS RecordPayoutSettled 를 보완).
 * <ul>
 * <li>지급이 기대한 상태일 때만 바꾼다 (조건부 UPDATE). 다른 상태면 아무것도 하지 않는다.</li>
 * <li>이미 목표 상태면 그대로 둔다: 같은 이벤트를 두 번 받아도 안전하다 (outbox 메시지도 한 번만).</li>
 * <li>가맹점 ID, 금액, 주소는 호출하는 쪽이 아니라 DB에서 읽는다.</li>
 * </ul>
 */
@Service
public class PayoutSettlementService {

	static final String MERCHANT_PAYOUT_SETTLED = "MERCHANT_PAYOUT_SETTLED";

	static final String MERCHANT_PAYOUT_FAILED = "MERCHANT_PAYOUT_FAILED";

	private static final Logger log = LoggerFactory.getLogger(PayoutSettlementService.class);

	private final PayoutRepositoryPort payouts;

	private final PayoutStatusHistoryPort history;

	private final PayoutDispatchStorePort dispatches;

	private final OutboxPort outbox;

	private final TransactionTemplate transaction;

	public PayoutSettlementService(PayoutRepositoryPort payouts, PayoutStatusHistoryPort history,
			PayoutDispatchStorePort dispatches, OutboxPort outbox, PlatformTransactionManager transactionManager) {
		this.payouts = payouts;
		this.history = history;
		this.dispatches = dispatches;
		this.outbox = outbox;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * 노드가 트랜잭션을 받았다: REQUESTED → AWAITING_CONFIRMATION, 전송 기록 SIGNED → BROADCAST.
	 */
	public boolean markBroadcast(UUID payoutId, String txHash) {
		return change(payoutId, PayoutStatus.REQUESTED, PayoutStatus.AWAITING_CONFIRMATION, "TRANSACTION_BROADCAST",
				txHash, DispatchStatus.SIGNED, DispatchStatus.BROADCAST, null, null);
	}

	/**
	 * 블록에서 성공했다: AWAITING_CONFIRMATION → SETTLED, 가맹점에 MERCHANT_PAYOUT_SETTLED 알림.
	 */
	public boolean settle(UUID payoutId, String txHash) {
		return change(payoutId, PayoutStatus.AWAITING_CONFIRMATION, PayoutStatus.SETTLED, "RECEIPT_CONFIRMED", txHash,
				DispatchStatus.BROADCAST, DispatchStatus.CONFIRMED, null, MERCHANT_PAYOUT_SETTLED);
	}

	/**
	 * 블록에서 실패했다(revert): AWAITING_CONFIRMATION → FAILED, 가맹점에 MERCHANT_PAYOUT_FAILED 알림.
	 */
	public boolean failOnChain(UUID payoutId, String txHash) {
		return change(payoutId, PayoutStatus.AWAITING_CONFIRMATION, PayoutStatus.FAILED, "TRANSACTION_REVERTED", txHash,
				DispatchStatus.BROADCAST, DispatchStatus.FAILED, "transaction reverted", MERCHANT_PAYOUT_FAILED);
	}

	/**
	 * 서명 단계에서 노드가 거부했다(잔액 부족 등). 체인에 아무것도 보내지 않았으므로 실패로 확정한다:
	 * REQUESTED → FAILED, 가맹점에 MERCHANT_PAYOUT_FAILED 알림.
	 */
	public boolean rejectBeforeSend(UUID payoutId, String error) {
		return change(payoutId, PayoutStatus.REQUESTED, PayoutStatus.FAILED, "TRANSFER_REJECTED", null,
				DispatchStatus.STARTED, DispatchStatus.FAILED, error, MERCHANT_PAYOUT_FAILED);
	}

	/**
	 * 결과를 알 수 없다. 돈이 나갔을 수도 있으므로 실패로 처리하지 않고 사람이 확인하게 한다.
	 */
	public boolean requireReview(UUID payoutId, PayoutStatus from, DispatchStatus dispatchFrom, String reason,
			String error) {
		return change(payoutId, from, PayoutStatus.REQUIRES_REVIEW, reason, null, dispatchFrom,
				DispatchStatus.REQUIRES_REVIEW, error, null);
	}

	private boolean change(UUID payoutId, PayoutStatus from, PayoutStatus to, String reason, String txHash,
			DispatchStatus dispatchFrom, DispatchStatus dispatchTo, String error, String merchantEvent) {
		Boolean changed = transaction.execute(status -> {
			if (!payouts.transition(payoutId, from, to, txHash)) {
				return false;
			}
			history.record(payoutId, from, to, reason);
			dispatches.transition(payoutId, dispatchFrom, dispatchTo, error);
			if (merchantEvent != null) {
				outbox.enqueue(OutboxMessage.of(CreatePayoutWithOutboxService.AGGREGATE_TYPE, payoutId, merchantEvent,
						merchantPayload(payoutId)));
			}
			return true;
		});
		if (Boolean.TRUE.equals(changed)) {
			log.info("Payout {} {} -> {} ({})", payoutId, from, to, reason);
			return true;
		}
		log.debug("Payout {} was not {}; {} skipped", payoutId, from, reason);
		return false;
	}

	/**
	 * 가맹점 알림 내용. 값은 모두 DB에서 읽는다.
	 */
	private Map<String, Object> merchantPayload(UUID payoutId) {
		Payout payout = payouts.findById(payoutId).orElseThrow();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("payoutId", payoutId.toString());
		payload.put("merchantId", payout.merchantId());
		payload.put("recipientAddress", payout.recipientAddress());
		payload.put("amountWei", payout.amountWei().toString());
		payouts.findTransactionHash(payoutId).ifPresent(hash -> payload.put("transactionHash", hash));
		return payload;
	}

}
