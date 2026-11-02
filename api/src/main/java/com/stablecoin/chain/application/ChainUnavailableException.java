package com.stablecoin.chain.application;

/**
 * 노드에 연결할 수 없을 때 발생한다 (연결 거부, timeout 등). 잠시 후 다시 시도할 수 있다.
 */
public class ChainUnavailableException extends RuntimeException {

	public ChainUnavailableException(Throwable cause) {
		super("chain is temporarily unavailable", cause);
	}

}
