package com.stablecoin.chain.application.port.out;

import com.stablecoin.chain.application.SignerNotConfiguredException;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * 트랜잭션 서명에 대한 출력 포트. 구현은 개인키를 보관하고 외부로 노출하지 않는다.
 * <p>
 * 개인키가 설정되지 않았으면 모든 메서드가 {@link SignerNotConfiguredException}을 던진다.
 */
public interface TransactionSignerPort {

	/**
	 * 서명 지갑의 주소.
	 */
	String address();

	/**
	 * 트랜잭션에 서명하고 노드에 보낼 hex 문자열(0x...)을 반환한다.
	 */
	String sign(UnsignedTransaction transaction);

}
