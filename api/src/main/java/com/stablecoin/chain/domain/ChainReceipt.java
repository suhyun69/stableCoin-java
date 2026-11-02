package com.stablecoin.chain.domain;

import java.math.BigInteger;

/**
 * 블록에 포함된 트랜잭션의 실행 결과.
 */
public record ChainReceipt(long blockNumber, boolean success, BigInteger gasUsed, BigInteger effectiveGasPrice) {

	public BigInteger fee() {
		return gasUsed.multiply(effectiveGasPrice);
	}

}
