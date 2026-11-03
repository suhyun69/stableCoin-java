package com.stablecoin.chain.application;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stablecoin.chain.application.port.in.SendTransactionUseCase;
import com.stablecoin.chain.application.port.in.TransferUseCase;
import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.chain.domain.Transfer;

/**
 * from 지갑에서 ETH를 바로 전송한다 (POST /api/chain/transfers). 서명과 전송을 지갑 잠금 안에서 이어서 하고,
 * 전송 후 receipt-timeout 동안 영수증을 기다린다.
 */
@Service
public class TransferService implements TransferUseCase {

	private static final Duration RECEIPT_POLL_INTERVAL = Duration.ofMillis(100);

	private final SendTransactionUseCase sendTransaction;

	private final WalletSendLocks walletLocks;

	private final ChainNodePort chainNode;

	private final Duration receiptTimeout;

	public TransferService(SendTransactionUseCase sendTransaction, WalletSendLocks walletLocks,
			ChainNodePort chainNode, @Value("${chain.receipt-timeout:2s}") Duration receiptTimeout) {
		this.sendTransaction = sendTransaction;
		this.walletLocks = walletLocks;
		this.chainNode = chainNode;
		this.receiptTimeout = receiptTimeout;
	}

	@Override
	public Transfer transfer(String from, String to, BigInteger wei) {
		SignedTransaction sent = walletLocks.withLock(from, () -> {
			SignedTransaction signed = sendTransaction.sign(from, to, wei, 0);
			sendTransaction.broadcast(signed);
			return signed;
		});
		return Transfer.of(sent.hash(), from, to, wei, sent.nonce(), waitForReceipt(sent.hash()).orElse(null));
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
