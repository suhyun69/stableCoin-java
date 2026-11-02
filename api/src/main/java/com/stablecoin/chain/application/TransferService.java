package com.stablecoin.chain.application;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stablecoin.chain.application.port.in.TransferUseCase;
import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.Transfer;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * from 지갑에서 ETH를 전송한다. nonce, 가스 한도, 수수료를 직접 채워 EIP-1559 트랜잭션에 서명한 뒤
 * eth_sendRawTransaction 으로 보낸다.
 */
@Service
public class TransferService implements TransferUseCase {

	private static final Duration RECEIPT_POLL_INTERVAL = Duration.ofMillis(100);

	/** baseFee가 연속으로 올라도 블록에 포함되도록 최대 수수료를 baseFee의 2배 + 우선 수수료로 잡는다. */
	private static final BigInteger BASE_FEE_MULTIPLIER = BigInteger.TWO;

	private final ChainNodePort chainNode;

	private final TransactionSignerPort signer;

	private final ChainIdGuard chainIdGuard;

	private final Duration receiptTimeout;

	/**
	 * 같은 지갑이 같은 nonce로 두 트랜잭션을 서명하지 않도록, 지갑별로 nonce 조회부터 전송까지 한 번에 하나씩만 처리한다.
	 * 다른 지갑끼리는 서로 기다리지 않는다. 이 인스턴스 안에서만 보장되며, 여러 인스턴스가 같은 지갑을 쓰면 별도의 순서 보장이 필요하다.
	 */
	private final ConcurrentMap<String, ReentrantLock> sendLocks = new ConcurrentHashMap<>();

	public TransferService(ChainNodePort chainNode, TransactionSignerPort signer, ChainIdGuard chainIdGuard,
			@Value("${chain.receipt-timeout:2s}") Duration receiptTimeout) {
		this.chainNode = chainNode;
		this.signer = signer;
		this.chainIdGuard = chainIdGuard;
		this.receiptTimeout = receiptTimeout;
	}

	@Override
	public Transfer transfer(String from, String to, BigInteger wei) {
		if (!signer.canSign(from)) {
			throw new UnknownSignerException(from);
		}
		BigInteger nonce;
		String txHash;
		ReentrantLock sendLock = sendLocks.computeIfAbsent(from.toLowerCase(Locale.ROOT), key -> new ReentrantLock());
		sendLock.lock();
		try {
			chainIdGuard.ensureVerified();
			nonce = chainNode.getPendingNonce(from);
			BigInteger gasLimit = chainNode.estimateGas(from, to, wei);
			BigInteger maxPriorityFee = chainNode.getMaxPriorityFeePerGas();
			BigInteger maxFee = chainNode.getLatestBaseFeePerGas().multiply(BASE_FEE_MULTIPLIER).add(maxPriorityFee);
			UnsignedTransaction tx = new UnsignedTransaction(chainIdGuard.expectedChainId(), nonce, to, wei, gasLimit,
					maxPriorityFee, maxFee);
			txHash = chainNode.sendRawTransaction(signer.sign(from, tx));
		}
		finally {
			sendLock.unlock();
		}
		return Transfer.of(txHash, from, to, wei, nonce.longValueExact(), waitForReceipt(txHash).orElse(null));
	}

	/**
	 * 영수증이 나올 때까지 receipt-timeout 동안 조회한다.
	 */
	private Optional<ChainReceipt> waitForReceipt(String txHash) {
		long deadline = System.nanoTime() + receiptTimeout.toNanos();
		while (true) {
			Optional<ChainReceipt> receipt = chainNode.getReceipt(txHash);
			if (receipt.isPresent() || System.nanoTime() >= deadline) {
				return receipt;
			}
			try {
				Thread.sleep(RECEIPT_POLL_INTERVAL);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
		}
	}

}
