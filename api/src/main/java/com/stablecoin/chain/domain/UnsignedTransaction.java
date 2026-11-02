package com.stablecoin.chain.domain;

import java.math.BigInteger;

/**
 * 서명 전 EIP-1559 ETH 전송 트랜잭션. chainId가 서명에 포함되어 다른 체인에서 재사용될 수 없다.
 */
public record UnsignedTransaction(long chainId, BigInteger nonce, String to, BigInteger value, BigInteger gasLimit,
		BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {
}
