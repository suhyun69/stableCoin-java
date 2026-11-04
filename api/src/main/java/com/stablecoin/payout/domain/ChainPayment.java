package com.stablecoin.payout.domain;

import java.math.BigInteger;

/**
 * 블록에서 성공한, 지급 지갑이 보낸 ETH 전송 한 건. transactionIndex 는 블록 안의 위치다.
 */
public record ChainPayment(String transactionHash, int transactionIndex, long blockNumber, String from, String to,
		BigInteger value) {
}
