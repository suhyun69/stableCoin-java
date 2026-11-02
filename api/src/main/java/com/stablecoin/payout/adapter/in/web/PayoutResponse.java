package com.stablecoin.payout.adapter.in.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stablecoin.chain.domain.Eth;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 지급 응답. 멱등성 키는 내보내지 않는다. 정밀도 손실을 막기 위해 금액은 문자열로 내보낸다.
 */
@Schema(description = "지급")
public record PayoutResponse(
		@Schema(description = "지급 ID") UUID id,
		@Schema(description = "가맹점 ID", example = "merchant_demo_01") String merchantId,
		@Schema(description = "받는 지갑", example = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8") String recipientAddress,
		@Schema(description = "지급 금액 (wei)", example = "25500000000000000000") String amountWei,
		@Schema(description = "지급 금액 (ETH)", example = "25.5") String amountEth,
		@Schema(description = "지급 상태") PayoutStatus status) {

	static PayoutResponse of(Payout payout) {
		return new PayoutResponse(payout.id(), payout.merchantId(), payout.recipientAddress(),
				payout.amountWei().toString(), Eth.format(payout.amountWei()), payout.status());
	}

}
