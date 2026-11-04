package com.stablecoin.payout.adapter.in.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stablecoin.chain.domain.Eth;
import com.stablecoin.payout.application.port.in.GetPayoutUseCase.PayoutView;
import com.stablecoin.payout.domain.ChainTransactionStatus;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutChainTransaction;
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
		@Schema(description = "체인 트랜잭션. 아직 서명하지 않았으면 null", nullable = true) Dispatch dispatch) {

	@Schema(description = "체인 트랜잭션 (chain_transactions)")
	public record Dispatch(
			@Schema(description = "체인 상태") ChainTransactionStatus status,
			@Schema(description = "보낸 지갑") String fromAddress,
			@Schema(description = "nonce") long nonce,
			@Schema(description = "트랜잭션 해시") String transactionHash,
			@Schema(description = "포함된 블록 번호. 블록에 들어가기 전이면 null", nullable = true) Long includedBlockNumber,
			@Schema(description = "쌓인 블록 수 (포함된 블록 포함)") long confirmations,
			@Schema(description = "실패하거나 확인 필요로 바뀐 이유", nullable = true) String lastError) {

		static Dispatch of(PayoutChainTransaction tx) {
			return (tx == null) ? null : new Dispatch(tx.status(), tx.senderAddress(), tx.nonce(), tx.transactionHash(),
					tx.includedBlockNumber(), tx.confirmations(), tx.lastError());
		}

	}

	static PayoutDetailResponse of(PayoutView view) {
		Payout payout = view.payout();
		return new PayoutDetailResponse(payout.id(), payout.merchantId(), payout.recipientAddress(),
				payout.amountWei().toString(), Eth.format(payout.amountWei()), payout.status(),
				Dispatch.of(view.chainTransaction()));
	}

}
