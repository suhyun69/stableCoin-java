package com.stablecoin.chain.application.port.in;

import java.util.List;

import com.stablecoin.chain.application.TransactionNotFoundException;
import com.stablecoin.chain.domain.Account;
import com.stablecoin.chain.domain.TransactionDetail;

/**
 * 체인 조회 입력 포트.
 */
public interface ChainQueryUseCase {

	long getChainId();

	/**
	 * 노드가 관리하는 지갑 목록과 잔액.
	 */
	List<Account> getAccounts();

	/**
	 * 앱이 서명할 수 있는 지갑(전송의 from으로 쓸 수 있는 지갑) 목록과 잔액.
	 */
	List<Account> getSigners();

	/**
	 * 앱이 이 지갑의 키를 갖고 있어 보낼 수 있는지. 노드를 호출하지 않는다.
	 */
	boolean canSign(String address);

	/**
	 * @throws TransactionNotFoundException 노드가 모르는 트랜잭션인 경우
	 */
	TransactionDetail getTransaction(String txHash);

}
