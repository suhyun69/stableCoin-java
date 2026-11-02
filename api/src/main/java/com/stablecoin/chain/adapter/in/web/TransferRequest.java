package com.stablecoin.chain.adapter.in.web;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.stablecoin.chain.domain.Eth;

/**
 * 전송 요청. 보내는 지갑은 앱의 서명 지갑으로 고정되어 요청에 받지 않는다.
 * 검증 메시지는 JVM 로케일과 관계없이 고정한다.
 */
@Schema(description = "전송 요청")
public record TransferRequest(
		@Schema(description = "받는 지갑", example = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8")
		@NotNull(message = "필수 값입니다")
		@Pattern(regexp = ADDRESS, message = "올바른 지갑 주소가 아닙니다")
		String to,

		@Schema(description = "전송 금액 (ETH, 소수점 18자리까지)", example = "1.5")
		@NotNull(message = "필수 값입니다")
		@Positive(message = "0보다 커야 합니다")
		@Digits(integer = 30, fraction = Eth.DECIMALS, message = "소수점 18자리까지 입력할 수 있습니다")
		BigDecimal amountEth) {

	static final String ADDRESS = "^0x[0-9a-fA-F]{40}$";

}
