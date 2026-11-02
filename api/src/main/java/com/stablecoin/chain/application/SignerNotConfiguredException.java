package com.stablecoin.chain.application;

/**
 * 서명용 개인키(chain.signer-private-key)가 설정되지 않아 전송할 수 없을 때 발생한다.
 */
public class SignerNotConfiguredException extends RuntimeException {

	public SignerNotConfiguredException() {
		super("signer private key is not configured (chain.signer-private-key)");
	}

}
