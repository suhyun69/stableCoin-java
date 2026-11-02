package com.stablecoin.chain.domain;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * ETH와 wei 단위 변환, JSON-RPC hex 값 변환.
 */
public final class Eth {

	public static final int DECIMALS = 18;

	private Eth() {
	}

	public static String format(BigInteger wei) {
		return new BigDecimal(wei).movePointLeft(DECIMALS).stripTrailingZeros().toPlainString();
	}

	public static BigInteger toWei(BigDecimal eth) {
		return eth.movePointRight(DECIMALS).toBigIntegerExact();
	}

	public static String toHex(BigInteger value) {
		return "0x" + value.toString(16);
	}

	public static BigInteger fromHex(String hex) {
		return new BigInteger(hex.substring(2), 16);
	}

	public static Long fromHexLong(String hex) {
		return (hex != null) ? fromHex(hex).longValueExact() : null;
	}

}
