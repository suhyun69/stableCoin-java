package com.stablecoin.chain;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지갑 주소와 잔액. 정밀도 손실을 막기 위해 금액은 문자열로 내보낸다.
 */
@Schema(description = "지갑 주소와 잔액")
public record Account(
		@Schema(description = "지갑 주소", example = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266") String address,
		@Schema(description = "잔액 (wei)", example = "10000000000000000000000") String balanceWei,
		@Schema(description = "잔액 (ETH)", example = "10000") String balanceEth) {

	private static final int ETH_DECIMALS = 18;

	public static Account of(String address, BigInteger wei) {
		String eth = new BigDecimal(wei).movePointLeft(ETH_DECIMALS).stripTrailingZeros().toPlainString();
		return new Account(address, wei.toString(), eth);
	}

}
