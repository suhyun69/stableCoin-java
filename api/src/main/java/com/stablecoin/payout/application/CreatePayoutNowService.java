package com.stablecoin.payout.application;

import org.springframework.stereotype.Service;

import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.application.port.out.PayoutTransferPort;
import com.stablecoin.payout.domain.Payout;

/**
 * 지급을 저장하고 바로 체인으로 보낸다 (stableCoin TS의 CreatePayoutNow).
 * <p>
 * <b>알려진 문제 (일부러 남겨 둠):</b>
 * <ul>
 * <li>Dual Write: ① DB 저장과 ② 체인 전송은 서로 다른 시스템이라 하나의 트랜잭션으로 묶이지 않는다. ①은 즉시 커밋되므로,
 * ②가 실패하거나 그 사이에 서버가 죽으면 "요청됨" 기록만 남고 돈은 나가지 않는다.</li>
 * <li>멱등성 없음: idempotencyKey를 저장만 하고 확인하지 않는다. 같은 키로 재시도하면 지급이 다시 저장되고 다시 전송된다.</li>
 * </ul>
 */
@Service
public class CreatePayoutNowService implements CreatePayoutUseCase {

	private final PayoutRepositoryPort payouts;

	private final PayoutTransferPort payoutTransfer;

	public CreatePayoutNowService(PayoutRepositoryPort payouts, PayoutTransferPort payoutTransfer) {
		this.payouts = payouts;
		this.payoutTransfer = payoutTransfer;
	}

	@Override
	public Payout create(CreatePayoutCommand command) {
		// ① DB 저장 (즉시 커밋)
		Payout payout = payouts.save(Payout.requested(command.merchantId(), command.recipientAddress(),
				command.amountWei(), command.idempotencyKey()));

		// !여기 이 지점: ①은 커밋됐고 ②는 아직이다. 여기서 실패하면 DB와 체인이 어긋난다.

		// ② 블록체인 전송
		payoutTransfer.transfer(payout);

		return payout;
	}

}
