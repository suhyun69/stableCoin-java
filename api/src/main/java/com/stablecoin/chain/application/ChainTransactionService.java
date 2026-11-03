package com.stablecoin.chain.application;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.stablecoin.chain.application.port.in.SendTransactionUseCase;
import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * EIP-1559 트랜잭션을 만들어 서명하고, 서명한 트랜잭션을 노드에 보낸다.
 */
@Service
public class ChainTransactionService implements SendTransactionUseCase {

	private static final Logger log = LoggerFactory.getLogger(ChainTransactionService.class);

	/** baseFee가 연속으로 올라도 블록에 포함되도록 최대 수수료를 baseFee의 2배 + 우선 수수료로 잡는다. */
	private static final BigInteger BASE_FEE_MULTIPLIER = BigInteger.TWO;

	private final ChainNodePort chainNode;

	private final TransactionSignerPort signer;

	private final ChainIdGuard chainIdGuard;

	public ChainTransactionService(ChainNodePort chainNode, TransactionSignerPort signer, ChainIdGuard chainIdGuard) {
		this.chainNode = chainNode;
		this.signer = signer;
		this.chainIdGuard = chainIdGuard;
	}

	@Override
	public SignedTransaction sign(String from, String to, BigInteger wei, long minNonce) {
		if (!signer.canSign(from)) {
			throw new UnknownSignerException(from);
		}
		chainIdGuard.ensureVerified();
		BigInteger nonce = chainNode.getPendingNonce(from).max(BigInteger.valueOf(minNonce));
		BigInteger gasLimit = chainNode.estimateGas(from, to, wei);
		BigInteger maxPriorityFee = chainNode.getMaxPriorityFeePerGas();
		BigInteger maxFee = chainNode.getLatestBaseFeePerGas().multiply(BASE_FEE_MULTIPLIER).add(maxPriorityFee);
		return signer.sign(from, new UnsignedTransaction(chainIdGuard.expectedChainId(), nonce, to, wei, gasLimit,
				maxPriorityFee, maxFee));
	}

	@Override
	public String broadcast(SignedTransaction transaction) {
		try {
			return chainNode.sendRawTransaction(transaction.raw());
		}
		catch (ChainException ex) {
			// 이미 보낸 트랜잭션을 다시 보낸 경우 노드는 "already known" 류의 오류를 낸다. 노드가 그 해시를 알면 성공이다.
			if (chainNode.getTransaction(transaction.hash()).isPresent()) {
				log.info("Transaction {} was already known to the node: {}", transaction.hash(), ex.getMessage());
				return transaction.hash();
			}
			throw ex;
		}
	}

}
