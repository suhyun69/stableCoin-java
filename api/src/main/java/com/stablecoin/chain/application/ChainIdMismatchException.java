package com.stablecoin.chain.application;

/**
 * 연결한 노드의 체인 ID가 설정(chain.id)과 다를 때 발생한다. 잘못된 체인에 서명한 트랜잭션을 보내지 않도록 막는다.
 */
public class ChainIdMismatchException extends RuntimeException {

	public ChainIdMismatchException(long expected, long actual) {
		super("chain id mismatch: expected " + expected + " (chain.id) but node reports " + actual);
	}

}
