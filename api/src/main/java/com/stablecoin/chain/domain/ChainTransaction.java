package com.stablecoin.chain.domain;

import java.math.BigInteger;

/**
 * 노드에 기록된 트랜잭션. 블록에 포함되기 전이면 blockNumber가 null이다.
 * gasPrice는 노드가 알려준 값이며, EIP-1559 트랜잭션이 대기 중이면 null일 수 있다.
 */
public record ChainTransaction(String hash, String from, String to, BigInteger value, long nonce, long gasLimit,
		BigInteger gasPrice, Long blockNumber, String input) {
}
