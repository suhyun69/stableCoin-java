package com.stablecoin.payout.adapter.in.web;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.stablecoin.chain.domain.Eth;

/**
 * 지급 요청. 검증 메시지는 JVM 로케일과 관계없이 고정한다.
 */
@Schema(description = "지급 요청")
public record CreatePayoutRequest(
		@Schema(description = "가맹점 ID", example = "merchant_demo_01")
		@NotBlank(message = "필수 값입니다")
		@Size(max = 64, message = "64자 이하여야 합니다")
		String merchantId,

		@Schema(description = "받는 지갑", example = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8")
		@NotNull(message = "필수 값입니다")
		@Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "올바른 지갑 주소가 아닙니다")
		String recipientAddress,

		@Schema(description = "지급 금액 (ETH, 소수점 18자리까지)", example = "25.5")
		@NotNull(message = "필수 값입니다")
		@Positive(message = "0보다 커야 합니다")
		@Digits(integer = 30, fraction = Eth.DECIMALS, message = "소수점 18자리까지 입력할 수 있습니다")
		BigDecimal amountEth) {
}
