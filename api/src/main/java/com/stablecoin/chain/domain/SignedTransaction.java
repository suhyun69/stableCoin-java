package com.stablecoin.chain.domain;

import java.math.BigInteger;

/**
 * 서명을 마친, 노드에 보낼 수 있는 트랜잭션. hash는 노드에 보내기 전에 서명 결과로 계산한 트랜잭션 해시다.
 * 같은 raw를 여러 번 보내도 같은 nonce라 한 번만 실행된다.
 */
public record SignedTransaction(String from, String to, BigInteger value, long nonce, String raw, String hash) {

	@Override
	public String toString() {
		return "SignedTransaction[from=" + from + ", to=" + to + ", value=" + value + ", nonce=" + nonce + ", hash="
				+ hash + "]";
	}

}
