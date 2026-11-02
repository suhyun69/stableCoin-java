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
	 * 서명 지갑의 주소와 잔액.
	 */
	Account getSigner();

	/**
	 * @throws TransactionNotFoundException 노드가 모르는 트랜잭션인 경우
	 */
	TransactionDetail getTransaction(String txHash);

}
