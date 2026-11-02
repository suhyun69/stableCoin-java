package com.stablecoin.chain.application;

/**
 * 노드가 JSON-RPC 오류를 반환했거나 응답이 비어 있을 때 발생한다.
 */
public class ChainException extends RuntimeException {

	public ChainException(String message) {
		super(message);
	}

}
