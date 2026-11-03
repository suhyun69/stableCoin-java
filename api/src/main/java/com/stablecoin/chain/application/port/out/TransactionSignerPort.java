package com.stablecoin.chain.application.port.out;

import java.util.List;

import com.stablecoin.chain.application.UnknownSignerException;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * 트랜잭션 서명에 대한 출력 포트. 구현은 지갑 개인키를 보관하고 외부로 노출하지 않는다.
 */
public interface TransactionSignerPort {

	/**
	 * 서명할 수 있는 지갑 주소 목록.
	 */
	List<String> addresses();

	boolean canSign(String address);

	/**
	 * from 지갑의 키로 트랜잭션에 서명하고, 노드에 보낼 hex(raw)와 트랜잭션 해시를 함께 돌려준다.
	 * @throws UnknownSignerException from 지갑의 키가 없는 경우
	 */
	SignedTransaction sign(String from, UnsignedTransaction transaction);

}
