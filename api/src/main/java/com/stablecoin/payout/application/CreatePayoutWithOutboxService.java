package com.stablecoin.payout.application;

import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.stablecoin.outbox.application.port.out.OutboxPort;
import com.stablecoin.outbox.domain.OutboxMessage;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.application.port.out.PayoutStatusHistoryPort;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 지급, 첫 상태 이력, 체인 전송 요청(outbox 메시지)을 <b>한 DB 트랜잭션</b>으로 저장한다 (stableCoin TS d5dd04b).
 * 셋 다 저장되거나 셋 다 저장되지 않으므로 "기록은 있는데 보낼 일이 없음" 같은 어긋남이 생기지 않는다.
 * 체인 전송은 여기서 하지 않고, outbox 발행기가 PAYOUT_REQUESTED 메시지를 읽어 처리한다.
 * <p>
 * 멱등성: 같은 Idempotency-Key로 같은 내용이 다시 오면 새로 만들지 않고 처음 지급을 돌려주고, 다른 내용이면 거부한다.
 * 동시에 같은 키로 두 요청이 와도 payouts의 UNIQUE 제약이 하나만 저장되게 막고, 진 쪽은 이긴 쪽의 결과를 돌려준다.
 */
@Service
public class CreatePayoutWithOutboxService implements CreatePayoutUseCase {

	static final String AGGREGATE_TYPE = "PAYOUT";

	static final String PAYOUT_REQUESTED = "PAYOUT_REQUESTED";

	static final String REASON_CREATED = "PAYOUT_CREATED";

	private final PayoutRepositoryPort payouts;

	private final PayoutStatusHistoryPort history;

	private final OutboxPort outbox;

	private final TransactionTemplate transaction;

	public CreatePayoutWithOutboxService(PayoutRepositoryPort payouts, PayoutStatusHistoryPort history,
			OutboxPort outbox, PlatformTransactionManager transactionManager) {
		this.payouts = payouts;
		this.history = history;
		this.outbox = outbox;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public CreatePayoutResult create(CreatePayoutCommand command) {
		try {
			return transaction.execute(status -> replay(command).orElseGet(() -> createNew(command)));
		}
		catch (DataIntegrityViolationException ex) {
			// 같은 키로 동시에 들어온 다른 요청이 먼저 커밋했다. 이 트랜잭션은 롤백됐으니 새 트랜잭션에서 그 결과를 읽는다.
			return transaction.execute(status -> replay(command).orElseThrow(() -> ex));
		}
	}

	/**
	 * 같은 키의 지급이 이미 있으면 내용이 같은지 확인하고 그대로 돌려준다.
	 */
	private Optional<CreatePayoutResult> replay(CreatePayoutCommand command) {
		return payouts.findByIdempotencyKey(command.idempotencyKey()).map(existing -> {
			String fingerprint = Payout.fingerprint(command.merchantId(), command.recipientAddress(),
					command.amountWei());
			if (!existing.requestFingerprint().equals(fingerprint)) {
				throw new IdempotencyKeyConflictException(command.idempotencyKey());
			}
			return new CreatePayoutResult(existing, true);
		});
	}

	private CreatePayoutResult createNew(CreatePayoutCommand command) {
		Payout payout = payouts.save(Payout.requested(command.merchantId(), command.recipientAddress(),
				command.amountWei(), command.idempotencyKey()));
		history.record(payout.id(), null, PayoutStatus.REQUESTED, REASON_CREATED);
		outbox.enqueue(OutboxMessage.of(AGGREGATE_TYPE, payout.id(), PAYOUT_REQUESTED,
				Map.of("payoutId", payout.id().toString(), "recipientAddress", payout.recipientAddress(),
						"amountWei", payout.amountWei().toString())));
		return new CreatePayoutResult(payout, false);
	}

}
