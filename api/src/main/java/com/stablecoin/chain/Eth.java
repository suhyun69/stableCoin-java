package com.stablecoin.chain;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * ETH와 wei 단위 변환.
 */
final class Eth {

	static final int DECIMALS = 18;

	private Eth() {
	}

	static String format(BigInteger wei) {
		return new BigDecimal(wei).movePointLeft(DECIMALS).stripTrailingZeros().toPlainString();
	}

	static BigInteger toWei(BigDecimal eth) {
		return eth.movePointRight(DECIMALS).toBigIntegerExact();
	}

	static String toHex(BigInteger value) {
		return "0x" + value.toString(16);
	}

	static BigInteger fromHex(String hex) {
		return new BigInteger(hex.substring(2), 16);
	}

}
