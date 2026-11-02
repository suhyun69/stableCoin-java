package com.stablecoin.chain.application;

/**
 * 앱이 키를 갖고 있지 않은 지갑에서 보내려 할 때 발생한다.
 */
public class UnknownSignerException extends RuntimeException {

	public UnknownSignerException(String address) {
		super("no signing key for wallet " + address + " (use one of GET /api/chain/signers)");
	}

}
