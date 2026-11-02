package com.stablecoin.chain;

/**
 * 노드가 해당 해시의 트랜잭션을 모를 때 발생한다.
 */
public class TransactionNotFoundException extends RuntimeException {

	public TransactionNotFoundException(String txHash) {
		super("트랜잭션을 찾을 수 없습니다: " + txHash);
	}

}
