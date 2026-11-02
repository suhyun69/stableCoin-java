package com.stablecoin.chain.application.port.out;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stablecoin.chain.application.ChainException;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;

/**
 * 블록체인 노드에 대한 출력 포트. 구현은 노드와 통신하는 방식(JSON-RPC 등)을 감춘다.
 * <p>
 * 모든 메서드는 노드에 연결할 수 없으면 {@link ChainUnavailableException}, 노드가 요청을 거부하면
 * {@link ChainException}을 던진다.
 */
public interface ChainNodePort {

	long getChainId();

	/**
	 * 노드가 관리하는 지갑 목록 (anvil 기본 지갑 등).
	 */
	List<String> getAccounts();

	/**
	 * 최신 블록 기준 잔액(wei).
	 */
	BigInteger getBalance(String address);

	/**
	 * 대기 중인 트랜잭션까지 포함한 다음 nonce.
	 */
	BigInteger getPendingNonce(String address);

	BigInteger estimateGas(String from, String to, BigInteger value);

	BigInteger getMaxPriorityFeePerGas();

	/**
	 * 최신 블록의 기본 수수료(baseFeePerGas).
	 */
	BigInteger getLatestBaseFeePerGas();

	/**
	 * 서명된 트랜잭션을 전송하고 트랜잭션 해시를 반환한다.
	 */
	String sendRawTransaction(String signedTransaction);

	Optional<ChainTransaction> getTransaction(String txHash);

	/**
	 * 블록에 포함되기 전이면 비어 있다.
	 */
	Optional<ChainReceipt> getReceipt(String txHash);

	Instant getBlockTimestamp(long blockNumber);

}
