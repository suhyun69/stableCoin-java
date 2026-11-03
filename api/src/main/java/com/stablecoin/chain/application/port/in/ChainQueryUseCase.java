package com.stablecoin.chain.application.port.in;

import java.util.List;
import java.util.Optional;

import com.stablecoin.chain.application.TransactionNotFoundException;
import com.stablecoin.chain.domain.Account;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;
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
	 * 블록에 포함된 트랜잭션의 실행 결과. 아직 포함되지 않았으면 비어 있다.
	 */
	Optional<ChainReceipt> getReceipt(String txHash);

	/**
	 * 노드가 이 트랜잭션을 알고 있는지 (대기 중이거나 블록에 포함됨).
	 */
	boolean isKnownTransaction(String txHash);

	/**
	 * 노드에 기록된 트랜잭션 원본 (보낸 지갑, 받는 지갑, 금액 등). 노드가 모르면 비어 있다.
	 */
	Optional<ChainTransaction> findTransaction(String txHash);

	/**
	 * 가장 최근 블록 번호.
	 */
	long getLatestBlockNumber();

	/**
	 * @throws TransactionNotFoundException 노드가 모르는 트랜잭션인 경우
	 */
	TransactionDetail getTransaction(String txHash);

}
