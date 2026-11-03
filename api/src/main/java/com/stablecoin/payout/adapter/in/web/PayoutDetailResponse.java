package com.stablecoin.payout.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stablecoin.chain.domain.Eth;
import com.stablecoin.payout.application.port.in.GetPayoutUseCase.PayoutView;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutDispatch;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 지급 조회 응답. 지급 상태와 체인 전송 상태를 나눠서 보여 준다. 서명한 트랜잭션 원본은 내보내지 않는다.
 */
@Schema(description = "지급과 체인 전송 상태")
public record PayoutDetailResponse(
		@Schema(description = "지급 ID") UUID id,
		@Schema(description = "가맹점 ID", example = "merchant_demo_01") String merchantId,
		@Schema(description = "받는 지갑", example = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8") String recipientAddress,
		@Schema(description = "지급 금액 (wei)", example = "25500000000000000000") String amountWei,
		@Schema(description = "지급 금액 (ETH)", example = "25.5") String amountEth,
		@Schema(description = "지급 상태") PayoutStatus status,
		@Schema(description = "체인 전송 기록. 소비자가 아직 처리하지 않았으면 null", nullable = true) Dispatch dispatch) {

	@Schema(description = "체인 전송 기록 (payout_dispatches)")
	public record Dispatch(
			@Schema(description = "전송 상태") DispatchStatus status,
			@Schema(description = "보낸 지갑. 서명 전이면 null", nullable = true) String fromAddress,
			@Schema(description = "nonce. 서명 전이면 null", nullable = true) Long nonce,
			@Schema(description = "트랜잭션 해시. 서명 전이면 null", nullable = true) String transactionHash,
			@Schema(description = "실패하거나 확인 필요로 바뀐 이유", nullable = true) String lastError,
			@Schema(description = "전송 기록이 마지막으로 바뀐 시각") Instant updatedAt) {

		static Dispatch of(PayoutDispatch dispatch) {
			return (dispatch == null) ? null : new Dispatch(dispatch.status(), dispatch.fromAddress(), dispatch.nonce(),
					dispatch.txHash(), dispatch.lastError(), dispatch.updatedAt());
		}

	}

	static PayoutDetailResponse of(PayoutView view) {
		Payout payout = view.payout();
		return new PayoutDetailResponse(payout.id(), payout.merchantId(), payout.recipientAddress(),
				payout.amountWei().toString(), Eth.format(payout.amountWei()), payout.status(),
				Dispatch.of(view.dispatch()));
	}

}
